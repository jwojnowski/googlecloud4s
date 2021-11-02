package me.wojnowski.googlecloud4s.auth

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.io.file.Files
import io.circe.parser._
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error._
import sttp.client3.SttpBackend

import java.nio.file.Paths
import java.time.Duration
import scala.util.control.NonFatal

trait TokenProvider[F[_]] {
  def getAccessToken(scopes: Scopes): F[AccessToken]

  def getIdentityToken(audience: TargetAudience): F[IdentityToken]
}

object TokenProvider {
  implicit def apply[F[_]](implicit ev: TokenProvider[F]): TokenProvider[F] = ev

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class JwtCreationFailure(cause: Throwable) extends Error

    case class UnexpectedResponse(details: String) extends Error

    case class InvalidJwt(cause: Throwable) extends Error
    case object NoExpirationInIdentityToken extends Error

    case class CommunicationError(cause: Throwable) extends Error
    case object AlgorithmNotFound extends Error
    case class InvalidCredentials(cause: Throwable) extends Error
  }

  def fromEnvironmentCached[F[_]: Async](expirationBuffer: Duration)(implicit sttpBackend: SttpBackend[F, Any]): F[TokenProvider[F]] =
    fromEnvironment[F].flatMap(CachingTokenProvider.instance(_, expirationBuffer))

  def fromEnvironment[F[_]: Async](implicit sttpBackend: SttpBackend[F, Any]): F[TokenProvider[F]] =
    Sync[F].delay(sys.env.get("GOOGLE_APPLICATION_CREDENTIALS")).flatMap {
      case Some(path) =>
        Files[F]
          .readAll(Paths.get(path), chunkSize = 4096)
          .through(fs2.text.utf8Decode)
          .compile
          .string
          .flatMap { rawCredentials =>
            Sync[F].fromEither(decode[Credentials](rawCredentials))
          }
          .adaptError {
            case NonFatal(throwable) => InvalidCredentials(throwable)
          }
          .flatMap(credentials => CredentialsTokenProvider.instance[F](credentials))
      case None       =>
        MetadataServerTokenProvider.instance[F].pure[F]
    }

}
