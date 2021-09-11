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
import io.circe.literal._
import sttp.client3._
import sttp.client3.circe._

import java.time.Instant
import fs2.Stream
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore._
import me.wojnowski.googlecloud4s.firestore.FirestoreCodec.syntax._
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.Codec
import io.circe.Decoder
import io.circe.HCursor
import io.circe.generic.extras.semiauto
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import io.circe.generic.semiauto.deriveDecoder
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.Scope
import me.wojnowski.googlecloud4s.auth.Token
import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter.Operator
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields.MyMap
import me.wojnowski.googlecloud4s.firestore.Firestore.Order.Direction

import scala.collection.immutable.SortedMap

trait Firestore[F[_]] {
  def add[V: FirestoreCodec](collection: Collection, value: V): F[String]

  def put[V: FirestoreCodec](collection: Collection, name: Name, value: V): F[Unit]

  def get[V: FirestoreCodec](collection: Collection, name: Name): F[Option[V]]

  /** @return old version, the last before successfully applying f */
  def update[V: FirestoreCodec](collection: Collection, name: Name, f: V => V): F[Option[V]]

  def batchGet[V: FirestoreCodec](collection: Collection, keys: NonEmptyList[String]): F[NonEmptyMap[String, Option[V]]]

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
  }

  object Name {

    case class Short(private val value: String) extends Name {
      def short: String = value
    }

    case class FullyQualified(private val value: String) extends Name {
      def short: String = value.split('/').last
      def full: String = value
    }

    def short(value: String): Name = Short(value)

    def fullyQualified(value: String): Name = FullyQualified(value)

    implicit val fullyQualifiedNameDecoder: Decoder[FullyQualified] = deriveUnwrappedDecoder

    implicit val show: Show[Name] = Show.fromToString
  }

  case class Collection(value: String) extends AnyVal {
    override def toString: String = value
  }

  object Collection {
    implicit val codec: Codec[Collection] = deriveUnwrappedCodec
  }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class AuthError(error: TokenProvider.Error) extends Error
    case class CommunicationError(cause: Throwable) extends Exception(cause) with Error
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
      deriveDecoder[FirestoreDocument]

    case class Fields(value: Map[String, FirestoreData]) {
      def toFirestoreData: FirestoreData =
        FirestoreData(JsonObject("mapValue" -> JsonObject("fields" -> Functor[MyMap].fmap(value)(_.json.asJson).asJson).asJson))
    }

    object Fields {

      type MyMap[A] = Map[String, A] // TODO WTF

      implicit val encoder: Encoder[Fields] =
        Encoder[Map[String, JsonObject]].contramap(x => Functor[MyMap].fmap(x.value)(_.json))

      implicit val decoder: Decoder[Fields] =
        Decoder[Map[String, JsonObject]].map(map => Fields(Functor[MyMap].fmap(map)(FirestoreData)))

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
        json"""
          {
            "fieldFilter": {
              "field": {
                "fieldPath": ${filter.fieldPath}
              },
              "op": ${filter.operator.value},
              "value": ${filter.value.json}
            }
          }
        """
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

  def instance[F[_]: Sync: Clock: TokenProvider](sttpBackend: SttpBackend[F, Any], projectId: ProjectId): Firestore[F] =
    new Firestore[F] {

      import sttp.client3._

      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      val baseUri = uri"https://firestore.googleapis.com"
      val scope = Scope("https://www.googleapis.com/auth/datastore")

      private def getToken: F[Either[Error.AuthError, Token]] =
        TokenProvider[F].getToken(scope).mapError2 {
          case e: TokenProvider.Error => Error.AuthError(e)
        }

      private def encodeFields[V: FirestoreCodec](value: V): F[Either[Error.EncodingFailure, FirestoreDocument.Fields]] =
        FirestoreDocument.Fields.fromFirestoreData(value.asFirestoreData).leftMap(Error.EncodingFailure).pure[F]

      override def add[V: FirestoreCodec](collection: Collection, value: V): F[String] = {
        for {
          _      <- Logger[F].debug(s"Adding to collection [$collection]...")
          token  <- getToken.rethrow
          fields <- encodeFields(value).rethrow
          result <- sttpBackend
                      .send {
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .post(uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection")
                          .body(JsonObject("fields" -> fields.asJson))
                          .response(asJson[Json])
                      }
                      .mapError(Error.CommunicationError)
                      .flatMap {
                        _.body match {
                          case Right(json)              =>
                            Sync[F].fromEither(extractId(json))
                          case Left(unexpectedResponse) =>
                            Error.UnexpectedResponse(unexpectedResponse.getMessage).raiseError[F, String]
                        }
                      }
        } yield result
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
                                 // TODO An actual optimistic locking
                                 .patch(uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection/${name.short}")
                                 .body(JsonObject("fields" -> encodedFields.asJson))
                                 .response(asJson[Json])
                             }
                             .mapError(Error.CommunicationError)
                             .flatMap {
                               _.body match {
                                 case Right(_)                =>
                                   Logger[F].info(s"Put [$name] into [$collection].") *>
                                     ().pure[F]
                                 case Left(responseException) =>
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

      override def batchGet[V: FirestoreCodec](collection: Collection, names: NonEmptyList[String]): F[NonEmptyMap[String, Option[V]]] =
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
                               .body(json"""{
                                      "documents": ${names
                                 .map(name => s"projects/${projectId.value}/databases/(default)/documents/$collection/$name}")}
                                    }""")
                               .response(asJson[List[HCursor]].getRight)
                           )
                           .flatMap { response =>
                             type EitherExceptionOr[A] = Either[Exception, A]

                             Sync[F]
                               .fromEither((response.body: List[HCursor]).traverse[EitherExceptionOr, (String, Option[V])] {
                                 hCursor => // TODO this is weird
                                   hCursor
                                     .downField("found")
                                     .as[FirestoreDocument]
                                     .flatMap { document =>
                                       document.as[V].map(document.name.short -> _.some)
                                     }
                                     .orElse(hCursor.downField("missing").as[String].map(_.split('/').last).map(_ -> none[V]))
                               })
                           }
          nonEmptyMap <- Sync[F].fromOption(NonEmptyMap.fromMap(SortedMap(results: _*)), Error.UnexpectedError("Empty response"))
        } yield nonEmptyMap

      override def get[V: FirestoreCodec](collection: Collection, name: Name): F[Option[V]] =
        Logger[F].debug(s"Getting [$collection/$name]...") *>
          getDocument(collection, name)
            .flatMap {
              _.traverse { document =>
                Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure))
              }
            }
            .flatTap { value =>
              Logger[F].debug(s"Got item [$collection/$name]: [$value]")
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  s"Failed to get item [$collection/$name] due to $throwable"
                )
            }

      override def update[V: FirestoreCodec](collection: Collection, name: Name, f: V => V): F[Option[V]] =
        Logger[F].debug(s"Updating [$collection/$name]...") *>
          getDocument(collection, name)
            .flatMap {
              _.traverse { document =>
                for {
                  decodedDocument <- Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure))
                  _               <- putWithOptimisticLocking(collection, name, f(decodedDocument), Some(document.updateTime))
                } yield decodedDocument
              } // TODO retries
            }
            .flatTap { previousValue =>
              Logger[F].trace(s"Updated [$collection/$name] from [$previousValue] to [${previousValue.map(f)}]") *>
                Logger[F].info(s"Updated [$collection/$name].")
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  s"Failed to update item [$collection/$name] due to $throwable"
                )
            }

      private def getDocument(collection: Collection, name: Name): F[Option[FirestoreDocument]] = {
        for {
          _      <- Logger[F].debug(s"Getting [$collection/$name]...")
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
                      .mapError(Error.CommunicationError)
                      .flatMap { response =>
                        response.body match {
                          case Right(json)                           =>
                            Logger[F].debug(s"Got [$collection/$name]") *>
                              json.some.pure[F]
                          case Left(_) if response.code.code === 404 =>
                            Logger[F].info(s"Couldn't get [$collection/$name]: not found") *>
                              none[FirestoreDocument].pure[F]
                          case Left(responseException)               =>
                            Error.UnexpectedResponse(responseException.getMessage).raiseError[F, Option[FirestoreDocument]]
                        }
                      }
        } yield result
      }.onError {
        case throwable => Logger[F].error(throwable)(s"Failed to get [$collection/$name] due to [$throwable]")
      }

      override def stream[V: FirestoreCodec](
        collection: Collection,
        filters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int = 50
      ): Stream[F, (Name.FullyQualified, Either[Error.DecodingFailure, V])] =
        streamOfDocuments(collection, filters, orderBy, pageSize)
          .map(document => document.name -> document.as[V].leftMap(Error.DecodingFailure))

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
        fieldFilters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int
      ): Stream[F, FirestoreDocument] = {
        def fetchPage(maybeLastName: Option[Name.FullyQualified], limit: Int) = {
          def createRequestBody(instant: Instant): Json = {
            val where = fieldFilters.toNel.map { fieldFilters =>
              json"""
                    {
                      "compositeFilter": {
                        "op": "AND",
                        "filters": $fieldFilters
                      }
                    }
                  """
            }

            val orderByJson = {
              orderBy match {
                case Nil  => List(Order("__name__", Direction.Ascending))
                case list => list
              }
            }.map { order =>
              json"""{"field": {"fieldPath": ${order.fieldPath}}, "direction": ${order.direction.productPrefix.toUpperCase}}"""
            }.asJson

            val query =
              json"""
                {
                    "from": [
                      {
                        "collectionId": $collection
                      }
                    ],
                    "limit": $limit,
                    "where": $where,
                    "orderBy": $orderByJson
                  }
                """.deepMerge(
                JsonObject
                  .fromIterable(maybeLastName.map { lastKey =>
                    "startAt" ->
                      json"""{
                        "values": [{"referenceValue": ${lastKey.full}}],
                        "before": false
                      }"""
                  })
                  .asJson
              )

            json"""
                {
                  "structuredQuery": $query,
                  "readTime": $instant
                }
                """
          }

          for {
            _       <- Logger[F].debug(
                         s"Streaming part (last document: [$maybeLastName], limit: [$limit])" +
                           s"of collection [$collection] with filters [$fieldFilters]..."
                       )
            token   <- getToken.rethrow
            instant <- Clock[F].realTimeInstant
            result  <- sttpBackend
                         .send(
                           basicRequest
                             .header("Authorization", s"Bearer ${token.value}")
                             .post(
                               uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents:runQuery"
                             )
                             .body(createRequestBody(instant))
                             .response(asJson[Json])
                         )
                         .mapError(Error.CommunicationError)
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
              s"Failed to stream part [last document name: [$maybeLastName], limit: [$limit] of [$collection] with filters [$fieldFilters] due to $throwable"
            )
        }

        def fetchRecursively(maybeLastName: Option[Name.FullyQualified], pageSize: Int): Stream[F, FirestoreDocument] =
          Stream.eval(fetchPage(maybeLastName, pageSize)).flatMap { batch =>
            Stream.chunk(Chunk.chain(batch)) ++
              batch
                .lastOption
                .fold[Stream[F, FirestoreDocument]](Stream.empty)(document => fetchRecursively(document.name.some, pageSize))
          }

        Stream.eval(Logger[F].debug(s"Streaming collection [$collection] with filters [$fieldFilters]...")).flatMap { _ =>
          fetchRecursively(maybeLastName = None, pageSize)
        }
      }

      override def delete[V](collection: Collection, name: Name): F[Unit] = {
        for {
          _      <- Logger[F].debug(s"Deleting [$collection/$name]...")
          token  <- getToken.rethrow
          result <- sttpBackend
                      .send {
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .delete(uri"$baseUri/v1/projects/${projectId.value}/databases/(default)/documents/$collection/${name.short}")
                      }
                      .mapError(Error.CommunicationError)
                      .flatMap { response =>
                        if (response.isSuccess)
                          Logger[F].info(s"Deleted [$collection/$name].")
                        else
                          Error.UnexpectedResponse(s"Expected success, got: [$response]").raiseError[F, Unit]
                      }
        } yield result
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(s"Failed to delete [$collection/$name] due to $throwable")
      }

      private def extractId(documentJson: Json): Either[Error.UnexpectedResponse, String] =
        documentJson
          .hcursor
          .downField("name")
          .as[String]
          .map(_.reverse.takeWhile(_ != '/').reverse)
          .leftMap(failure => Error.UnexpectedResponse(s"Couldn't decode document name: ${failure.getMessage}"))

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
