package me.wojnowski.googlecloud4s

import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.Json
import io.circe.parser.parse
import me.wojnowski.googlecloud4s.auth.TokenProvider
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.model.StatusCode

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

trait Storage[F[_]] {
  def put(bucket: Bucket, key: Key, stream: Stream[F, Byte], maybeContentLength: Option[Long] = None): F[Unit]

  def delete(bucket: Bucket, key: Key): F[Unit]

  def get(bucket: Bucket, key: Key): F[Option[Stream[F, Byte]]]

  def exists(bucket: Bucket, key: Key): F[Boolean]

  def list(bucket: Bucket): F[List[Key]]
}

object Storage {

  implicit def apply[F[_]](implicit ev: Storage[F]): Storage[F] = ev

  def instance[F[_]: Sync](
    sttpBackend: SttpBackend[F, Fs2Streams[F]],
    timeout: Duration = 600.seconds
  )(
    implicit tokenProvider: TokenProvider[F]
  ): Storage[F] =
    new Storage[F] {
      import sttp.client3._

      private val Scope = auth.Scope("https://www.googleapis.com/auth/devstorage.read_write")

      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      override def put(
        bucket: Bucket,
        key: Key,
        stream: fs2.Stream[F, Byte],
        maybeContentLength: Option[Long]
      ): F[Unit] =
        for {
          _     <- Logger[F].debug(show"Putting [$bucket/$key]...")
          token <- TokenProvider[F].getToken(Scope)
          _     <- basicRequest
                     .post(uri"https://storage.googleapis.com/upload/storage/v1/b/${bucket.value}/o?uploadType=media&name=${key.value}")
                     .header("Authorization", s"Bearer ${token.value}")
                     .header("Content-Type", "application/octet-stream")
                     .readTimeout(timeout)
                     .streamBody(Fs2Streams[F])(stream)
                     .send(sttpBackend)
                     .flatMap { response =>
                       response.body match {
                         case Right(_)        => ().pure[F]
                         case Left(errorBody) => UnexpectedResponseException(response, Some(errorBody)).raiseError[F, Unit]
                       }
                     }
          _     <- Logger[F].info(show"Put [$bucket/$key].")
        } yield ()

      override def delete(bucket: Bucket, key: Key): F[Unit] =
        for {
          _     <- Logger[F].debug(show"Deleting [$bucket/$key]...")
          token <- TokenProvider[F].getToken(Scope)
          _     <- basicRequest
                     .delete(uri"https://storage.googleapis.com/storage/v1/b/${bucket.value}/o/${key.value}")
                     .header("Authorization", s"Bearer ${token.value}")
                     .send(sttpBackend)
                     .flatMap { response =>
                       response.body match {
                         case Right(_)                                        => ().pure[F]
                         case Left(_) if response.code == StatusCode.NotFound => ().pure[F]
                         case Left(errorBody)                                 =>
                           UnexpectedResponseException(response, Some(errorBody)).raiseError[F, Unit]
                       }
                     }
          _     <- Logger[F].debug(show"Deleted [$bucket/$key].")
        } yield ()

      override def get(bucket: Bucket, key: Key): F[Option[Stream[F, Byte]]] =
        for {
          _      <- Logger[F].debug(show"Getting [$bucket/$key]...")
          token  <- TokenProvider[F].getToken(Scope)
          result <- basicRequest
                      .get(uri"https://storage.googleapis.com/storage/v1/b/${bucket.value}/o/${key.value}?alt=media")
                      .header("Authorization", s"Bearer ${token.value}")
                      .response(asStreamUnsafe(Fs2Streams[F]))
                      .readTimeout(timeout)
                      .send(sttpBackend)
                      .flatMap { response =>
                        response.body match {
                          case Right(stream)                                   => stream.some.pure[F]
                          case Left(_) if response.code == StatusCode.NotFound => none[Stream[F, Byte]].pure[F]
                          case Left(errorBody)                                 =>
                            UnexpectedResponseException(response, Some(errorBody)).raiseError[F, Option[Stream[F, Byte]]]
                        }
                      }
          _      <- Logger[F].debug(show"Got [$bucket/$key] stream")
        } yield result

      override def list(bucket: Bucket): F[List[Key]] =
        for {
          _      <- Logger[F].debug(show"Listing bucket [$bucket]...")
          token  <- TokenProvider[F].getToken(Scope)
          result <- basicRequest
                      .get(uri"https://storage.googleapis.com/storage/v1/b/${bucket.value}/o")
                      .header("Authorization", s"Bearer ${token.value}")
                      .send(sttpBackend)
                      .flatMap { response =>
                        response.body match {
                          case Right(data)     =>
                            Sync[F].fromEither {
                              parse(data).flatMap { json =>
                                json.hcursor.downField("items").as[List[Json]].flatMap { objects =>
                                  objects
                                    .traverse { x =>
                                      (x.hcursor.downField("name").as[String], x.hcursor.downField("kind").as[String]).mapN {
                                        (name, kind) =>
                                          if (kind === "storage#object") Some(Key(name))
                                          else None
                                      }
                                    }
                                    .map(_.collect { case Some(key) => key })
                                }
                              }
                            }
                          case Left(errorBody) =>
                            UnexpectedResponseException(response, Some(errorBody)).raiseError[F, List[Key]]
                        }
                      }
          _      <- Logger[F].debug(show"Listed bucket [$bucket].")
        } yield result

      override def exists(bucket: Bucket, key: Key): F[Boolean] =
        for {
          _      <- Logger[F].debug(show"Checking existence of [$bucket/$key]...")
          token  <- TokenProvider[F].getToken(Scope)
          result <- basicRequest
                      .get(uri"https://storage.googleapis.com/storage/v1/b/${bucket.value}/o/${key.value}?alt=json")
                      .header("Authorization", s"Bearer ${token.value}")
                      .send(sttpBackend)
                      .flatMap { response =>
                        response.body match {
                          case Right(_)                                        =>
                            true.pure[F]
                          case Left(_) if response.code == StatusCode.NotFound =>
                            false.pure[F]
                          case Left(errorBody)                                 =>
                            UnexpectedResponseException(response, Some(errorBody)).raiseError[F, Boolean]
                        }
                      }
        } yield result

    }

  case class UnexpectedResponseException(status: Int, message: String, headers: Map[String, String] = Map.empty)
    extends ProductSerializableNoStacktrace {
    override def toString: String = s"UnexpectedResponseException($status, $message, $headers)"
  }

  object UnexpectedResponseException {

    def apply[T](response: sttp.client3.Response[T], message: Option[String]) =
      new UnexpectedResponseException(
        response.code.code,
        message.getOrElse(response.body.toString),
        response.headers.map(header => (header.name, header.value)).toMap
      )

  }

}
