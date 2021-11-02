package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Sync
import io.circe.HCursor
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error.UnexpectedResponse
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.syntax.all._
import me.wojnowski.googlecloud4s.auth.IdentityToken
import pdi.jwt.Jwt
import pdi.jwt.JwtOptions
import sttp.client3.SttpBackend

import java.time.Instant
import scala.util.control.NonFatal
import TokenProvider.Error

object MetadataServerTokenProvider {

  def instance[F[_]: Sync](implicit sttpBackend: SttpBackend[F, Any]): TokenProvider[F] =
    new TokenProvider[F] {
      import sttp.client3._
      import sttp.client3.circe._

      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      override def getAccessToken(scopes: Scopes): F[AccessToken] = {
        for {
          instant <- Clock[F].realTimeInstant
          _       <- Logger[F].debug(s"Getting fresh access token with scope [$scopes] from metadata server...")
          token   <- sttpBackend
                       .send(
                         basicRequest
                           .header("Metadata-Flavor", "Google")
                           .get(
                             uri"http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
                               .addParam("scopes", scopes.values.toList.mkString(","))
                           )
                           .response(asJsonAlways[HCursor])
                       )
                       .flatMap { response =>
                         response.body match {
                           case Right(value) =>
                             Sync[F].fromEither {
                               (
                                 value.get[String]("access_token"),
                                 value.get[Int]("expires_in")
                               ).mapN { (accessToken, expiresInSeconds) =>
                                 AccessToken(accessToken, scopes, instant.plusSeconds(expiresInSeconds.toLong))
                               }.leftMap(error => UnexpectedResponse(s"Couldn't decode response due to ${error.getMessage}"))
                             }
                           case Left(error)  =>
                             UnexpectedResponse(s"Couldn't decode response due to ${error.getMessage}").raiseError[F, AccessToken]
                         }
                       }
        } yield token
      }
        .flatTap(_ => logger.info(s"Successfully got access token for scope [$scopes] from metadata server."))
        .onError { case error => logger.error(s"Error while getting access token for scope [$scopes] from metadata server: $error!") }

      override def getIdentityToken(audience: TargetAudience): F[IdentityToken] =
        sttpBackend
          .send(
            basicRequest
              .header("Metadata-Flavor", "Google")
              .get(uri"http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=${audience.value}")
          )
          .flatMap { response =>
            response.body match {
              case Right(rawJwt) =>
                Sync[F]
                  .fromTry(Jwt.decode(rawJwt, JwtOptions.DEFAULT.copy(signature = false)))
                  .adaptError {
                    case NonFatal(throwable) => Error.InvalidJwt(throwable)
                  }
                  .flatMap { jwt =>
                    Sync[F]
                      .fromOption(jwt.expiration, Error.NoExpirationInIdentityToken)
                      .map(Instant.ofEpochSecond)
                  }
                  .map { expiration =>
                    IdentityToken(rawJwt, audience, expiration)
                  }
              case Left(error)   =>
                Error.UnexpectedResponse(s"Response status: ${response.code}, details: $error").raiseError[F, IdentityToken]
            }

          }

    }

}
