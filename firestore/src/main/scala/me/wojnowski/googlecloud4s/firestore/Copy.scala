package me.wojnowski.googlecloud4s.firestore

import cats.effect.Sync
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.firestore.Copy.Error.BatchWriteFailed

import java.time.Instant

// TODO think about the name
trait Copy[F[_]] {
  def copy(source: Reference.Document, target: Reference.Document): F[Unit]

  def copyCollectionsRecursively(
    source: Reference.NonCollection,
    target: Reference.NonCollection,
    chunkSize: Int = 64,
    readTime: Option[Instant] = None
  ): F[Unit]

  def copyRecursively(
    source: Reference.Collection,
    target: Reference.Collection,
    chunkSize: Int = 64,
    readTime: Option[Instant] = None
  ): F[Unit]

}

object Copy {

  def instance[F[_]: Sync](firestore: Firestore[F]): Copy[F] =
    new Copy[F] {
      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      override def copy(source: Reference.Document, target: Reference.Document): F[Unit] = {
        for {
          _     <- Logger[F].debug(show"Copying [$source] to [$target]...")
          value <- firestore.get[Value](source).flatMap(_.liftTo[F](Error.DocumentNotFound(source)))
          _     <- firestore.set(target, value)
          _     <- Logger[F].info(show"Copied [$source] to [$target].")
        } yield ()
      }.onError {
        case throwable => Logger[F].error(throwable)(s"Failed to copy [$source] to [$target] due to: $throwable")
      }

      override def copyCollectionsRecursively(
        source: Reference.NonCollection,
        target: Reference.NonCollection,
        chunkSize: Int = 64,
        readTime: Option[Instant] = None
      ): F[Unit] = {
        for {
          _ <- Sync[F].raiseWhen(target.contains(source) && readTime.isEmpty)(Firestore.Error.TargetIsInSource(source, target))
          _ <- Logger[F].debug(show"Copying recursively [$source] to [$target]...")
          _ <- firestore
                 .listCollectionIds(source, readTime = readTime)
                 .evalMap(collectionId => copyRecursively(source / collectionId, target / collectionId, chunkSize, readTime))
                 .compile
                 .drain
          _ <- Logger[F].info(show"Copied recursively [$source] to [$target].")
        } yield ()
      }.onError {
        case throwable => Logger[F].error(throwable)(s"Failed to recursively copy [$source] to [$target] due to: $throwable")
      }

      override def copyRecursively(
        source: Reference.Collection,
        target: Reference.Collection,
        chunkSize: Int = 64,
        readTime: Option[Instant] = None
      ): F[Unit] = {
        for {
          _     <- Sync[F].raiseWhen(target.contains(source) && readTime.isEmpty)(Firestore.Error.TargetIsInSource(source, target))
          _     <- Logger[F].debug(show"Copying recursively [$source] to [$target]...")
          count <- firestore
                     .listDocuments(source, showMissing = true, pageSize = chunkSize.some, readTime)
                     .chunkN(chunkSize)
                     .evalTap { documents =>
                       val maybeWrites =
                         documents
                           .collect { case (reference, Some(document)) => reference -> document }
                           .map {
                             case (Reference.Document(_, documentId), fields) =>
                               Write.Update(target / documentId, fields)
                           }
                           .toNel

                       maybeWrites.traverse { writes =>
                         firestore
                           .batchWrite(target.root, writes)
                           .flatMap { response =>
                             Sync[F].raiseUnless(response.writeResults.forall(_.updateTime.isDefined))(BatchWriteFailed(source, response))
                           }
                           .as(documents.map { case (Reference.Document(_, documentId), _) => documentId })
                       }
                     }
                     .unchunks
                     .map { case (Reference.Document(_, documentId), _) => documentId }
                     .evalMap(documentId => copyCollectionsRecursively(source / documentId, target / documentId, chunkSize, readTime))
                     .compile
                     .count
          _     <- Logger[F].info(show"Copied recursively [$count] documents [$source] to [$target].")
        } yield ()
      }.onError {
        case throwable => Logger[F].error(throwable)(s"Failed to recursively copy [$source] to [$target] due to: $throwable")
      }

    }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class DocumentNotFound(reference: Reference.Document) extends Error
    case class BatchWriteFailed(source: Reference.Collection, batchWriteResponse: BatchWriteResponse) extends Error
  }

}
