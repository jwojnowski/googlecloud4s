package me.wojnowski.googlecloud4s.firestore

import cats.Functor
import cats.Show
import cats.data.Chain
import cats.data.NonEmptyList
import cats.data.NonEmptyMap
import cats.effect.Clock
import cats.effect.Sync
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import sttp.client3._
import sttp.client3.circe._

import java.time.Instant
import fs2.Stream
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore._
import me.wojnowski.googlecloud4s.firestore.FirestoreCodec.syntax._
import cats.implicits._
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import io.circe.HCursor
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.AccessToken
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter.Operator
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields.MyMap
import me.wojnowski.googlecloud4s.firestore.Firestore.Order.Direction
import sttp.model.StatusCode

import scala.collection.immutable.SortedMap

trait Firestore[F[_]] {
  def add[V: FirestoreCodec](collection: Collection, value: V): F[Name.FullyQualified]

  def put[V: FirestoreCodec](collection: Collection, name: Name, value: V): F[Unit]

  def get[V: FirestoreCodec](collection: Collection, name: Name): F[Option[V]]

  /** @return old version, the last before successfully applying f */
  def update[V: FirestoreCodec](collection: Collection, name: Name, f: V => V): F[Option[V]]

  def batchGet[V: FirestoreCodec](collection: Collection, keys: NonEmptyList[Name]): F[NonEmptyMap[Name.FullyQualified, Option[V]]]

  // TODO create some Query class
  def stream[V: FirestoreCodec](
    collection: Collection,
    filters: List[FieldFilter] = List.empty,
    orderBy: List[Order] = List.empty,
    pageSize: Int = 50
  ): Stream[F, (Name.FullyQualified, Either[Error.DecodingFailure, V])]

  def streamLogFailures[V: FirestoreCodec](
    collection: Collection,
    filters: List[FieldFilter] = List.empty,
    orderBy: List[Order] = List.empty,
    pageSize: Int = 50
  ): Stream[F, (Name.FullyQualified, V)]

  def delete[V](collection: Collection, name: Name): F[Unit]
}

object Firestore {
  def apply[F[_]](implicit ev: Firestore[F]): Firestore[F] = ev

  sealed trait Name extends Product with Serializable {
    def short: String

    def toShort: Name.Short

    def toFull(projectId: ProjectId, collection: Collection): Name.FullyQualified
  }

  object Name {

    case class Short(private val value: String) extends Name {
      def short: String = value

      override def toShort: Short = this

      override def toFull(projectId: ProjectId, collection: Collection): Name.FullyQualified =
        FullyQualified(s"projects/${projectId.value}/databases/(default)/documents/${collection.value}/$short")
    }

    // TODO (ProjectId, Collection, Short) and a cats-parse parser
    case class FullyQualified(private val value: String) extends Name {
      def short: String = value.split('/').last

      def full: String = value

      override def toShort: Short = Short(short)

      override def toFull(projectId: ProjectId, collection: Collection): FullyQualified = this
    }

    def short(value: String): Name = Short(value)

    def fullyQualified(value: String): Name = FullyQualified(value)

    implicit val fullyQualifiedNameDecoder: Decoder[FullyQualified] = Decoder[String].map(FullyQualified.apply)
    implicit val ordering: Ordering[FullyQualified] = Ordering.by(_.full)

    implicit val show: Show[Name] = Show.fromToString
  }

  case class Collection(value: String) extends AnyVal {
    override def toString: String = value
  }

  object Collection {

    implicit val encoder: Encoder[Collection] = Encoder[String].contramap(_.value)
    implicit val decoder: Decoder[Collection] = Decoder[String].map(Collection.apply)

  }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class AuthError(error: TokenProvider.Error) extends Error
    case class CommunicationError(cause: Throwable) extends Exception(cause) with Error
    case object OptimisticLockingFailure extends Error
    case class GenericError(override val getMessage: String) extends Error
    case class UnexpectedResponse(override val getMessage: String) extends Error
    case class UnexpectedError(override val getMessage: String) extends Error
    case class DecodingFailure(cause: Throwable) extends Exception(cause) with Error
    case class EncodingFailure(override val getMessage: String) extends Error
  }

  case class FirestoreDocument(name: Name.FullyQualified, fields: Fields, updateTime: Instant) {
    def as[V: FirestoreCodec]: Either[FirestoreCodec.Error, V] = fields.toFirestoreData.as[V]
  }

  object FirestoreDocument {

    implicit val decoder: Decoder[FirestoreDocument] =
      Decoder.forProduct3[FirestoreDocument, Name.FullyQualified, Fields, Instant]("name", "fields", "updateTime")(FirestoreDocument.apply)

    case class Fields(value: Map[String, FirestoreData]) {
      def toFirestoreData: FirestoreData =
        FirestoreData(JsonObject("mapValue" -> JsonObject("fields" -> Functor[MyMap].fmap(value)(_.json.asJson).asJson).asJson))
    }

    object Fields {

      type MyMap[A] = Map[String, A] // TODO WTF

      implicit val encoder: Encoder[Fields] =
        Encoder[Map[String, JsonObject]].contramap(x => Functor[MyMap].fmap(x.value)(_.json))

      implicit val decoder: Decoder[Fields] =
        Decoder[Map[String, JsonObject]].map(map => Fields(Functor[MyMap].fmap(map)(FirestoreData.apply)))

      def fromFirestoreData(data: FirestoreData): Either[String, Fields] =
        data
          .json
          .asJson
          .hcursor
          .downField("mapValue")
          .downField("fields")
          .as[Map[String, JsonObject]]
          .map(fields => Fields(Functor[MyMap].fmap(fields)(obj => FirestoreData(obj))))
          .leftMap(_.getMessage) // FIXME
    }

  }

  case class FieldFilter(fieldPath: String, value: FirestoreData, operator: Operator)

  object FieldFilter {

    def apply[V: FirestoreCodec](fieldPath: String, value: V, operator: Operator): FieldFilter =
      FieldFilter(fieldPath, value.asFirestoreData, operator)

    implicit val encoder: Encoder[FieldFilter] =
      Encoder.instance { filter =>
        JsonObject(
          "fieldFilter" -> JsonObject(
            "field" -> JsonObject(
              "fieldPath" -> filter.fieldPath.asJson
            ).asJson,
            "op" -> filter.operator.value.asJson,
            "value" -> filter.value.json.asJson
          ).asJson
        ).asJson
      }

    sealed trait Operator extends Product with Serializable {
      def value: String
    }

    object Operator {
      case object In extends Operator { val value = "IN" }
      case object < extends Operator { val value = "LESS_THAN" }
      case object > extends Operator { val value = "GREATER_THAN" }
      case object >= extends Operator { val value = "GREATER_THAN_OR_EQUAL" }
      case object <= extends Operator { val value = "LESS_THAN_OR_EQUAL" }
      case object == extends Operator { val value = "EQUAL" }
      case object =!= extends Operator { val value = "NOT_EQUAL" }
      case class Other(value: String) extends Operator
    }

  }

  case class Order(fieldPath: String, direction: Direction)

  object Order {
    sealed trait Direction extends Product with Serializable

    object Direction {
      case object Ascending extends Direction
      case object Descending extends Direction
    }

  }

  def instance[F[_]: Sync: TokenProvider](
    sttpBackend: SttpBackend[F, Any],
    projectId: ProjectId,
    uriOverride: Option[String Refined refined.string.Uri] = None,
    optimisticLockingAttempts: Int = 16
  ): Firestore[F] =
    new Firestore[F] {

      import sttp.client3._

      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      val baseUri = uriOverride.fold(uri"https://firestore.googleapis.com")(u => uri"$u")
      val scope = Scopes("https://www.googleapis.com/auth/datastore")

      private def getToken: F[Either[Error.AuthError, AccessToken]] =
        TokenProvider[F].getAccessToken(scope).mapError2 {
          case e: TokenProvider.Error => Error.AuthError(e)
        }

      private def encodeFields[V: FirestoreCodec](value: V): F[Either[Error.EncodingFailure, FirestoreDocument.Fields]] =
        FirestoreDocument.Fields.fromFirestoreData(value.asFirestoreData).leftMap(Error.EncodingFailure.apply).pure[F]

      override def add[V: FirestoreCodec](collection: Collection, value: V): F[Name.FullyQualified] = {
        for {
          _      <- Logger[F].debug(s"Adding to collection [$collection]...")
          token  <- getToken.rethrow
          fields <- encodeFields(value).rethrow
          name   <- sttpBackend
                      .send {
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .post(uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection")
                          .body(JsonObject("fields" -> fields.asJson))
                          .response(asJson[Json])
                      }
                      .mapError(Error.CommunicationError.apply)
                      .flatMap {
                        _.body match {
                          case Right(json)              =>
                            Sync[F].fromEither(extractName(json))
                          case Left(unexpectedResponse) =>
                            Error.UnexpectedResponse(unexpectedResponse.getMessage).raiseError[F, Name.FullyQualified]
                        }
                      }
        } yield name
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(
            s"Failed to add new item to collection [$collection] due to $throwable"
          )
      }

      override def put[V: FirestoreCodec](collection: Collection, name: Name, value: V): F[Unit] =
        putWithOptimisticLocking(collection, name, value, maybeUpdateTime = None)

      private def putWithOptimisticLocking[V: FirestoreCodec](
        collection: Collection,
        name: Name,
        value: V,
        maybeUpdateTime: Option[Instant]
      ): F[Unit] = {
        for {
          _             <- Logger[F].debug(s"Putting [$name] into [$collection]...")
          token         <- getToken.rethrow
          encodedFields <- encodeFields(value).rethrow
          _             <- sttpBackend
                             .send {
                               basicRequest
                                 .header("Authorization", s"Bearer ${token.value}")
                                 .patch(
                                   uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection/${name.short}".addParams(
                                     Map() ++ maybeUpdateTime.map(updateTime => "currentDocument.updateTime" -> updateTime.toString)
                                   )
                                 )
                                 .body(JsonObject("fields" -> encodedFields.asJson))
                                 .response(asJsonEither[FirestoreErrorResponse, Json])
                             }
                             .mapError(Error.CommunicationError.apply)
                             .flatMap {
                               _.body match {
                                 case Right(_)                                                                              =>
                                   Logger[F].info(s"Put [$name] into [$collection].") *>
                                     ().pure[F]
                                 case Left(HttpError(FirestoreErrorResponse("FAILED_PRECONDITION"), StatusCode.BadRequest)) =>
                                   Error.OptimisticLockingFailure.raiseError[F, Unit]
                                 case Left(responseException)                                                               =>
                                   Error.UnexpectedResponse(responseException.getMessage).raiseError[F, Unit]
                               }
                             }
        } yield ()
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(
            s"Failed to put item [$name] into collection [$collection] due to $throwable"
          )
      }

      override def batchGet[V: FirestoreCodec](
        collection: Collection,
        names: NonEmptyList[Name]
      ): F[NonEmptyMap[Name.FullyQualified, Option[V]]] =
        for {
          _           <- Logger[F].debug(s"Getting in a batch documents [$names] from collection [$collection]...")
          token       <- getToken.rethrow
          results     <- sttpBackend
                           .send(
                             basicRequest
                               .header("Authorization", s"Bearer ${token.value}")
                               .post(
                                 uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents:batchGet"
                               )
                               .body(
                                 JsonObject(
                                   "documents" ->
                                     names
                                       .map(name => name.toFull(projectId, collection).full)
                                       .asJson
                                 ).asJson
                               )
                               .response(asJson[List[HCursor]].getRight)
                           )
                           .flatMap { response =>
                             type EitherExceptionOr[A] = Either[Exception, A]

                             Sync[F]
                               .fromEither((response.body: List[HCursor]).traverse[EitherExceptionOr, (Name.FullyQualified, Option[V])] {
                                 hCursor => // TODO this is weird
                                   hCursor
                                     .downField("found")
                                     .as[FirestoreDocument]
                                     .flatMap { document =>
                                       document.as[V].map(document.name -> _.some)
                                     }
                                     .orElse(hCursor.downField("missing").as[String].map(Name.FullyQualified.apply).map(_ -> none[V]))
                               })
                           }
          nonEmptyMap <- Sync[F].fromOption(NonEmptyMap.fromMap(SortedMap(results: _*)), Error.UnexpectedError("Empty response"))
        } yield nonEmptyMap

      override def get[V: FirestoreCodec](collection: Collection, name: Name): F[Option[V]] =
        Logger[F].debug(s"Getting [$collection/${name.short}]...") *>
          getDocument(collection, name)
            .flatMap {
              _.traverse { document =>
                Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure.apply))
              }
            }
            .flatTap { value =>
              Logger[F].trace(s"Got item [$collection/${name.short}]: [$value]") *>
                Logger[F].debug(s"Got item [$collection/${name.short}].")
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  s"Failed to get item [$collection/${name.short}] due to $throwable"
                )
            }

      override def update[V: FirestoreCodec](collection: Collection, name: Name, f: V => V): F[Option[V]] = {
        def attemptUpdate(attemptsLeft: Int): F[Option[V]] =
          getDocument(collection, name)
            .flatMap {
              _.traverse { document =>
                for {
                  decodedDocument <- Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure.apply))
                  _               <- putWithOptimisticLocking(collection, name, f(decodedDocument), Some(document.updateTime))
                } yield decodedDocument
              }
            }
            .flatTap { previousValue =>
              Logger[F].trace(s"Updated [$collection/${name.short}] from [$previousValue] to [${previousValue.map(f)}]") *>
                Logger[F].info(s"Updated [$collection/${name.short}].")
            }
            .recoverWith {
              case Error.OptimisticLockingFailure if attemptsLeft > 1 =>
                Logger[F].debug(
                  s"Encounter optimistic locking failure while updating [$collection/${name.short}]. Attempts left: ${attemptsLeft - 1}"
                ) *> attemptUpdate(attemptsLeft - 1)
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  s"Failed to update item [$collection/${name.short}] due to $throwable"
                )
            }

        Logger[F].debug(s"Updating [$collection/${name.short}]...") *> attemptUpdate(optimisticLockingAttempts)
      }

      private def getDocument(collection: Collection, name: Name): F[Option[FirestoreDocument]] = {
        for {
          _      <- Logger[F].debug(s"Getting [$collection/${name.short}]...")
          token  <- getToken.rethrow
          result <- sttpBackend
                      .send(
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .get(
                            uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection/${name.short}"
                          )
                          .response(asJson[FirestoreDocument])
                      )
                      .mapError(Error.CommunicationError.apply)
                      .flatMap { response =>
                        response.body match {
                          case Right(json)                           =>
                            Logger[F].debug(s"Got [$collection/${name.short}]") *>
                              json.some.pure[F]
                          case Left(_) if response.code.code === 404 =>
                            Logger[F].info(s"Couldn't get [$collection/${name.short}]: not found") *>
                              none[FirestoreDocument].pure[F]
                          case Left(responseException)               =>
                            Error.UnexpectedResponse(responseException.getMessage).raiseError[F, Option[FirestoreDocument]]
                        }
                      }
        } yield result
      }.onError {
        case throwable => Logger[F].error(throwable)(s"Failed to get [$collection/${name.short}] due to [$throwable]")
      }

      override def stream[V: FirestoreCodec](
        collection: Collection,
        filters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int = 50
      ): Stream[F, (Name.FullyQualified, Either[Error.DecodingFailure, V])] =
        streamOfDocuments(collection, filters, orderBy, pageSize)
          .map(document => document.name -> document.as[V].leftMap(Error.DecodingFailure.apply))

      override def streamLogFailures[V: FirestoreCodec](
        collection: Collection,
        filters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int = 50
      ): Stream[F, (Name.FullyQualified, V)] =
        stream(collection, filters, orderBy, pageSize).flatMap {
          case (key, Right(value)) =>
            Stream.emit(key -> value)
          case (name, Left(error)) =>
            Stream
              .eval(
                Logger[F].error(show"Couldn't decode [$name] due to error [${error.toString}]")
              )
              .flatMap(_ => Stream.empty)
        }

      private def streamOfDocuments(
        collection: Collection,
        fieldFilters: List[FieldFilter],
        orderBy: List[Order],
        pageSize: Int
      ): Stream[F, FirestoreDocument] = {
        def fetchPage(maybeLast: Option[FirestoreDocument], readTime: Instant, limit: Int) = {
          def createRequestBody: Json = {
            val where = fieldFilters.toNel.map { fieldFilters =>
              JsonObject(
                "compositeFilter" -> JsonObject(
                  "op" -> "AND".asJson,
                  "filters" -> fieldFilters.asJson
                ).asJson
              )
            }

            val orderByWithName = orderBy ++ List(Order("__name__", Direction.Ascending))

            val orderByJson = orderByWithName.map { order =>
              JsonObject(
                "field" -> JsonObject("fieldPath" -> order.fieldPath.asJson).asJson,
                "direction" -> order.direction.productPrefix.toUpperCase.asJson
              ).asJson
            }.asJson

            val query =
              JsonObject(
                "from" -> List(
                  JsonObject("collectionId" -> collection.asJson)
                ).asJson,
                "limit" -> limit.asJson,
                "where" -> where.asJson,
                "orderBy" -> orderByJson
              ).asJson
                .deepMerge(
                  JsonObject
                    .fromIterable(maybeLast.map { lastDocument =>
                      val values =
                        orderBy.map(order => lastDocument.fields.value.apply(order.fieldPath)) :+
                          FirestoreData(
                            JsonObject("referenceValue" -> lastDocument.name.full.asJson)
                          )

                      "startAt" ->
                        JsonObject(
                          "values" -> values.map(_.json.asJson).asJson,
                          "before" -> false.asJson
                        ).asJson
                    })
                    .asJson
                )
            JsonObject(
              "structuredQuery" -> query.asJson,
              "readTime" -> readTime.asJson
            ).asJson
          }

          for {
            _      <- Logger[F].debug(
                        s"Streaming part (last document: [${maybeLast.map(_.name)}], limit: [$limit])" +
                          s"of collection [$collection] with filters [$fieldFilters]..."
                      )
            token  <- getToken.rethrow
            request = basicRequest
                        .header("Authorization", s"Bearer ${token.value}")
                        .post(
                          uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents:runQuery"
                        )
                        .body(createRequestBody)
                        .response(asJson[Json])
            result <- sttpBackend
                        .send(
                          request
                        )
                        .mapError(Error.CommunicationError.apply)
                        .flatMap { response =>
                          response.body match {
                            case Right(jsons)            =>
                              jsons
                                .as[Chain[JsonObject]]
                                .map(_.filter(_.contains("document")))
                                .flatMap(_.traverse(_.asJson.hcursor.downField("document").as[FirestoreDocument])) match { // TODO this could use a refactor
                                case Right(result) =>
                                  result.pure[F]
                                case Left(error)   =>
                                  Error
                                    .UnexpectedResponse(
                                      s"Couldn't decode streaming response due to [${error.getMessage}]"
                                    )
                                    .raiseError[F, Chain[FirestoreDocument]]
                              }
                            case Left(responseException) =>
                              Error
                                .UnexpectedResponse(responseException.getMessage)
                                .raiseError[F, Chain[FirestoreDocument]]
                          }
                        }
          } yield result
        }.onError {
          case throwable =>
            Logger[F].error(throwable)(
              s"Failed to stream part [last document name: [$maybeLast], limit: [$limit] of [$collection] with filters [$fieldFilters] due to $throwable"
            )
        }

        def fetchRecursively(maybeLast: Option[FirestoreDocument], readTime: Instant, pageSize: Int): Stream[F, FirestoreDocument] =
          Stream.eval(fetchPage(maybeLast, readTime, pageSize)).flatMap { batch =>
            Stream.chunk(Chunk.chain(batch)) ++
              batch
                .lastOption
                .fold[Stream[F, FirestoreDocument]](Stream.empty)(document => fetchRecursively(document.some, readTime, pageSize))
          }

        Stream
          .eval {
            Clock[F].realTimeInstant.flatTap { readTime =>
              Logger[F].debug(s"Streaming collection [$collection] with filters [$fieldFilters] and read time [$readTime]...")
            }
          }
          .flatMap { readTime =>
            fetchRecursively(maybeLast = None, readTime, pageSize)
          }
      }

      override def delete[V](collection: Collection, name: Name): F[Unit] = {
        for {
          _      <- Logger[F].debug(s"Deleting [$collection/${name.short}]...")
          token  <- getToken.rethrow
          result <- sttpBackend
                      .send {
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .delete(uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection/${name.short}")
                      }
                      .mapError(Error.CommunicationError.apply)
                      .flatMap { response =>
                        if (response.isSuccess)
                          Logger[F].info(s"Deleted [$collection/${name.short}].")
                        else
                          Error.UnexpectedResponse(s"Expected success, got: [$response]").raiseError[F, Unit]
                      }
        } yield result
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(s"Failed to delete [$collection/${name.short}] due to $throwable")
      }

      private def extractName(documentJson: Json): Either[Error.UnexpectedResponse, Name.FullyQualified] =
        documentJson
          .hcursor
          .downField("name")
          .as[String]
          .map(Name.FullyQualified.apply)
          .leftMap(failure => Error.UnexpectedResponse(s"Couldn't decode document name: ${failure.getMessage}"))

    }

  private case class FirestoreErrorResponse(status: String)

  private object FirestoreErrorResponse {

    implicit val decoder: Decoder[FirestoreErrorResponse] =
      Decoder.instance(_.downField("error").downField("status").as[String].map(FirestoreErrorResponse.apply))

  }

  // TODO rename class and methods
  implicit class MapError[F[_]: Sync, A](fa: F[A]) {

    def mapError2[E <: Throwable](f: PartialFunction[Throwable, E]): F[Either[E, A]] =
      fa.attempt.flatMap {
        case Right(a)        =>
          a.asRight[E].pure[F]
        case Left(throwable) =>
          f.lift.apply(throwable).fold(throwable.raiseError[F, Either[E, A]])(_.asLeft[A].pure[F])
      }

    def mapError[E <: Throwable](f: Throwable => E): F[A] =
      fa.adaptError {
        case x => f(x)
      }

  }

}
