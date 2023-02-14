package me.wojnowski.googlecloud4s.firestore

import cats.Functor
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
import me.wojnowski.googlecloud4s.firestore.Firestore.Order.Direction
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.Segment

import scala.collection.immutable.SortedMap
import scala.util.control.NonFatal

trait Firestore[F[_]] {
  def add[V: FirestoreCodec](collectionReference: Reference.Collection, value: V): F[Reference.Document]

  def set[V: FirestoreCodec](reference: Reference.Document, value: V): F[Unit]

  def get[V: FirestoreCodec](reference: Reference.Document): F[Option[V]]

  /** @return old version, the last before successfully applying f */
  def update[V: FirestoreCodec](reference: Reference.Document, f: V => V): F[Option[V]]

  def batchGet[V: FirestoreCodec](paths: NonEmptyList[Reference.Document]): F[NonEmptyMap[Reference.Document, Option[V]]]

  // TODO create some Query class
  def stream[V: FirestoreCodec](
    reference: Reference.Collection,
    filters: List[FieldFilter] = List.empty,
    orderBy: List[Order] = List.empty,
    pageSize: Int = 50
  ): Stream[F, (Reference.Document, Either[Error.DecodingFailure, V])]

  def streamLogFailures[V: FirestoreCodec](
    reference: Reference.Collection,
    filters: List[FieldFilter] = List.empty,
    orderBy: List[Order] = List.empty,
    pageSize: Int = 50
  ): Stream[F, (Reference.Document, V)]

  def delete(reference: Reference.Document): F[Unit]

  def rootReference: Reference.Root
}

object Firestore {
  private[firestore] val defaultDatabase = "(default)"

  def apply[F[_]](implicit ev: Firestore[F]): Firestore[F] = ev

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
    case class ReferencesDontMatchRoot(notMatchingReferences: NonEmptyList[Reference.Document], root: Reference.Root)
      extends IllegalArgumentException(s"References [$notMatchingReferences] don't match project root [$root]")
  }

  case class FirestoreDocument(reference: Reference.Document, fields: Fields, updateTime: Instant) {
    def as[V: FirestoreCodec]: Either[FirestoreCodec.Error, V] = fields.toFirestoreData.as[V]
  }

  object FirestoreDocument {

    implicit val decoder: Decoder[FirestoreDocument] =
      Decoder.forProduct3[FirestoreDocument, Reference.Document, Fields, Instant]("name", "fields", "updateTime")(FirestoreDocument.apply)

    case class Fields(value: Map[String, FirestoreData]) {
      def toFirestoreData: FirestoreData =
        FirestoreData(JsonObject("mapValue" -> JsonObject("fields" -> Functor[Map[String, *]].fmap(value)(_.json.asJson).asJson).asJson))
    }

    object Fields {

      implicit val encoder: Encoder[Fields] =
        Encoder[Map[String, JsonObject]].contramap(x => Functor[Map[String, *]].fmap(x.value)(_.json))

      implicit val decoder: Decoder[Fields] =
        Decoder[Map[String, JsonObject]].map(map => Fields(Functor[Map[String, *]].fmap(map)(FirestoreData.apply)))

      def fromFirestoreData(data: FirestoreData): Either[String, Fields] =
        data
          .json
          .asJson
          .hcursor
          .downField("mapValue")
          .downField("fields")
          .as[Map[String, JsonObject]]
          .map(fields => Fields(Functor[Map[String, *]].fmap(fields)(obj => FirestoreData(obj))))
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

      override def rootReference: Reference.Root = Reference.Root(projectId)

      private def getToken: F[AccessToken] =
        TokenProvider[F].getAccessToken(scope).adaptError {
          case e: TokenProvider.Error => Error.AuthError(e)
        }

      private def encodeFields[V: FirestoreCodec](value: V): F[Either[Error.EncodingFailure, FirestoreDocument.Fields]] =
        FirestoreDocument.Fields.fromFirestoreData(value.asFirestoreData).leftMap(Error.EncodingFailure.apply).pure[F]

      override def add[V: FirestoreCodec](collection: Reference.Collection, value: V): F[Reference.Document] = {
        for {
          _         <- Logger[F].debug(s"Adding to collection [${collection.full}]...")
          token     <- getToken
          fields    <- encodeFields(value).rethrow
          reference <- sttpBackend
                         .send {
                           basicRequest
                             .header("Authorization", s"Bearer ${token.value}")
                             .post(createUri(collection))
                             .body(JsonObject("fields" -> fields.asJson))
                             .response(asJson[Json])
                         }
                         .adaptError { case NonFatal(throwable) => Error.CommunicationError(throwable) }
                         .flatMap {
                           _.body match {
                             case Right(json)              =>
                               Sync[F].fromEither(extractReference(json))
                             case Left(unexpectedResponse) =>
                               Error.UnexpectedResponse(unexpectedResponse.getMessage).raiseError[F, Reference.Document]
                           }
                         }
          _         <- Logger[F].info(s"Added item [$reference].")
        } yield reference
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(
            s"Failed to add new item to collection [${collection.full}] due to $throwable"
          )
      }

      override def set[V: FirestoreCodec](reference: Reference.Document, value: V): F[Unit] =
        setWithOptimisticLocking(reference, value, maybeUpdateTime = None)

      private def setWithOptimisticLocking[V: FirestoreCodec](
        reference: Reference.Document,
        value: V,
        maybeUpdateTime: Option[Instant]
      ): F[Unit] = {
        for {
          _             <- Logger[F].debug(show"Putting [$reference]...")
          token         <- getToken
          encodedFields <- encodeFields(value).rethrow
          _             <- sttpBackend
                             .send {
                               basicRequest
                                 .header("Authorization", s"Bearer ${token.value}")
                                 .patch(
                                   createUri(reference).addParams(
                                     Map() ++ maybeUpdateTime.map(updateTime => "currentDocument.updateTime" -> updateTime.toString)
                                   )
                                 )
                                 .body(JsonObject("fields" -> encodedFields.asJson))
                                 .response(asJsonEither[FirestoreErrorResponse, Json])
                             }
                             .adaptError { case NonFatal(throwable) => Error.CommunicationError(throwable) }
                             .flatMap {
                               _.body match {
                                 case Right(_)                                                                              =>
                                   Logger[F].info(s"Put [$reference].") *>
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
            show"Failed to put item [$reference] due to ${throwable.toString}"
          )
      }

      override def batchGet[V: FirestoreCodec](
        paths: NonEmptyList[Reference.Document]
      ): F[NonEmptyMap[Reference.Document, Option[V]]] =
        for {
          _           <- Sync[F].fromEither(validateReferencesAgainstProjectRoot(paths))
          _           <- Logger[F].debug(s"Getting in a batch documents [$paths]...")
          token       <- getToken
          results     <- sttpBackend
                           .send(
                             basicRequest
                               .header("Authorization", s"Bearer ${token.value}")
                               .post(createUri(rootReference, ":batchGet"))
                               .body(
                                 JsonObject(
                                   "documents" ->
                                     paths
                                       .map(_.full)
                                       .asJson
                                 ).asJson
                               )
                               .response(asJson[List[HCursor]].getRight)
                           )
                           .flatMap { response =>
                             type EitherExceptionOr[A] = Either[Exception, A]

                             Sync[F]
                               .fromEither((response.body: List[HCursor]).traverse[EitherExceptionOr, (Reference.Document, Option[V])] {
                                 hCursor => // TODO this is weird
                                   hCursor
                                     .downField("found")
                                     .as[FirestoreDocument]
                                     .flatMap { document =>
                                       document.as[V].map(document.reference -> _.some)
                                     }
                                     .orElse(
                                       hCursor
                                         .downField("missing")
                                         .as[String]
                                         .flatMap(Reference.Document.parse(_).leftMap(new IllegalArgumentException(_)))
                                         .map(_ -> none[V])
                                     )
                               })
                           }
          nonEmptyMap <- Sync[F].fromOption(NonEmptyMap.fromMap(SortedMap(results: _*)), Error.UnexpectedError("Empty response"))
        } yield nonEmptyMap

      private def validateReferencesAgainstProjectRoot(
        references: NonEmptyList[Reference.Document]
      ): Either[Error.ReferencesDontMatchRoot, Unit] = {
        val notMatchingReferences = references.filter(_.contains(rootReference))
        notMatchingReferences.toNel.toRight(()).swap.leftMap(Error.ReferencesDontMatchRoot(_, rootReference))
      }

      override def get[V: FirestoreCodec](reference: Reference.Document): F[Option[V]] =
        Logger[F].debug(show"Getting [$reference]...") *>
          getDocument(reference)
            .flatMap {
              _.traverse { document =>
                Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure.apply))
              }
            }
            .flatTap { value =>
              Logger[F].trace(s"Got item [$reference]: [$value]") *>
                Logger[F].debug(show"Got item [$reference].")
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  show"Failed to get item [$reference] due to ${throwable.toString}"
                )
            }

      override def update[V: FirestoreCodec](reference: Reference.Document, f: V => V): F[Option[V]] = {
        def attemptUpdate(attemptsLeft: Int): F[Option[V]] =
          getDocument(reference)
            .flatMap {
              _.traverse { document =>
                for {
                  decodedDocument <- Sync[F].fromEither(document.as[V].leftMap(Error.DecodingFailure.apply))
                  _               <- setWithOptimisticLocking(reference, f(decodedDocument), Some(document.updateTime))
                } yield decodedDocument
              }
            }
            .flatTap { previousValue =>
              Logger[F].trace(s"Updated [$reference] from [$previousValue] to [${previousValue.map(f)}]") *>
                Logger[F].info(show"Updated [$reference].")
            }
            .recoverWith {
              case Error.OptimisticLockingFailure if attemptsLeft > 1 =>
                Logger[F].debug(
                  show"Encounter optimistic locking failure while updating [$reference]. Attempts left: ${attemptsLeft - 1}"
                ) *> attemptUpdate(attemptsLeft - 1)
            }
            .onError {
              case throwable =>
                Logger[F].error(throwable)(
                  show"Failed to update item [$reference] due to ${throwable.toString}"
                )
            }

        Logger[F].debug(show"Updating [$reference]...") *> attemptUpdate(optimisticLockingAttempts)
      }

      private def getDocument(reference: Reference.Document): F[Option[FirestoreDocument]] = {
        for {
          _      <- Logger[F].debug(show"Getting [$reference]...")
          token  <- getToken
          result <- sttpBackend
                      .send(
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .get(
                            createUri(reference)
                          )
                          .response(asJson[FirestoreDocument])
                      )
                      .adaptError { case NonFatal(throwable) => Error.CommunicationError(throwable) }
                      .flatMap { response =>
                        response.body match {
                          case Right(json)                           =>
                            Logger[F].debug(show"Got [$reference].") *>
                              json.some.pure[F]
                          case Left(_) if response.code.code === 404 =>
                            Logger[F].info(show"Couldn't get [$reference]: not found") *>
                              none[FirestoreDocument].pure[F]
                          case Left(responseException)               =>
                            Error.UnexpectedResponse(responseException.getMessage).raiseError[F, Option[FirestoreDocument]]
                        }
                      }
        } yield result
      }.onError {
        case throwable => Logger[F].error(throwable)(show"Failed to get [$reference] due to [${throwable.toString}]")
      }

      override def stream[V: FirestoreCodec](
        collection: Reference.Collection,
        filters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int = 50
      ): Stream[F, (Reference.Document, Either[Error.DecodingFailure, V])] =
        streamOfDocuments(collection, filters, orderBy, pageSize)
          .map(document => document.reference -> document.as[V].leftMap(Error.DecodingFailure.apply))

      override def streamLogFailures[V: FirestoreCodec](
        collection: Reference.Collection,
        filters: List[FieldFilter] = List.empty,
        orderBy: List[Order] = List.empty,
        pageSize: Int = 50
      ): Stream[F, (Reference.Document, V)] =
        stream(collection, filters, orderBy, pageSize).flatMap {
          case (key, Right(value))      =>
            Stream.emit(key -> value)
          case (reference, Left(error)) =>
            Stream
              .eval(
                Logger[F].error(show"Couldn't decode [$reference] due to error [${error.toString}]")
              )
              .flatMap(_ => Stream.empty)
        }

      private def streamOfDocuments(
        collection: Reference.Collection,
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
                  JsonObject("collectionId" -> collection.collectionId.asJson)
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
                            JsonObject("referenceValue" -> lastDocument.reference.full.asJson)
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
                        s"Streaming part (last document: [${maybeLast.map(_.reference)}], limit: [$limit])" +
                          s"of collection [${collection.full}] with filters [$fieldFilters]..."
                      )
            token  <- getToken
            request = basicRequest
                        .header("Authorization", s"Bearer ${token.value}")
                        .post(createUri(collection.parent, ":runQuery"))
                        .body(createRequestBody)
                        .response(asJson[Json])
            result <- sttpBackend
                        .send(
                          request
                        )
                        .adaptError { case NonFatal(throwable) => Error.CommunicationError(throwable) }
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
              s"Failed to stream part [last document name: [$maybeLast], limit: [$limit] of [${collection.full}] with filters [$fieldFilters] due to $throwable"
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
              Logger[F].debug(s"Streaming collection [${collection.full}] with filters [$fieldFilters] and read time [$readTime]...")
            }
          }
          .flatMap { readTime =>
            fetchRecursively(maybeLast = None, readTime, pageSize)
          }
      }

      override def delete(reference: Reference.Document): F[Unit] = {
        for {
          _      <- Logger[F].debug(show"Deleting [$reference]...")
          token  <- getToken
          result <- sttpBackend
                      .send {
                        basicRequest
                          .header("Authorization", s"Bearer ${token.value}")
                          .delete(createUri(reference))
                      }
                      .adaptError { case NonFatal(throwable) => Error.CommunicationError(throwable) }
                      .flatMap { response =>
                        if (response.isSuccess)
                          Logger[F].info(show"Deleted [$reference].")
                        else
                          Error.UnexpectedResponse(s"Expected success, got: [$response]").raiseError[F, Unit]
                      }
        } yield result
      }.onError {
        case throwable =>
          Logger[F].error(throwable)(show"Failed to delete [$reference] due to ${throwable.toString}")
      }

      private def extractReference(documentJson: Json): Either[Error.UnexpectedResponse, Reference.Document] =
        documentJson
          .hcursor
          .downField("name")
          .as[String]
          .flatMap(Reference.Document.parse)
          .leftMap(failure => Error.UnexpectedResponse(s"Couldn't decode document name: ${failure.getMessage}"))

      private def createUri(reference: Reference, suffix: String = ""): Uri =
        baseUri.addPath("v1").addPathSegment(Segment(reference.full + suffix, encoding = identity))

    }

  private case class FirestoreErrorResponse(status: String)

  private object FirestoreErrorResponse {

    implicit val decoder: Decoder[FirestoreErrorResponse] =
      Decoder.instance(_.downField("error").downField("status").as[String].map(FirestoreErrorResponse.apply))

  }

}
