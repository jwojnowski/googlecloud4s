package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._
import io.circe.Json
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import sttp.client3.SttpBackend

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal

trait TokenProvider[F[_]] {
  def getToken(scope: Scope): F[Token]
}

object TokenProvider {
  implicit def apply[F[_]](implicit ev: TokenProvider[F]): TokenProvider[F] = ev

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class JwtCreationFailure(cause: Throwable) extends Error

    case class UnexpectedResponse(details: String) extends Error

    case class CommunicationError(cause: Throwable) extends Error
    case object AlgorithmNotFound extends Error
    case class InvalidCredentials(cause: Throwable) extends Error
  }

  def cachedInstance[F[_]: Sync](
    credentials: Credentials,
    expirationBuffer: Duration
  )(
    implicit sttpBackend: SttpBackend[F, Any]
  ): F[TokenProvider[F]] =
    for {
      ref      <- Ref.of[F, Map[Scope, Token]](Map.empty)
      instance <- instance[F](credentials)
      now      <- Clock[F].realTimeInstant
    } yield new TokenProvider[F] {

      override def getToken(scope: Scope): F[Token] =
        ref.get.map(_.get(scope).filter(_.expires.minus(expirationBuffer).isAfter(now))).flatMap {
          case Some(token) => token.pure[F]
          case None        => instance.getToken(scope).flatTap(token => ref.update(_.updated(scope, token)))
        }

    }

  def instance[F[_]: Sync](credentials: Credentials)(implicit sttpBackend: SttpBackend[F, Any]): F[TokenProvider[F]] = {
    implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

    def decodePrivateKey(keyFactory: KeyFactory, rawKey: String): F[RSAPrivateKey] =
      Sync[F].delay {
        val bytes =
          Base64
            .getDecoder
            .decode(
              rawKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\n", "")
            )

        keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes)).asInstanceOf[RSAPrivateKey]
      }

    for {
      factory <- Sync[F].delay(KeyFactory.getInstance("RSA")).adaptError {
                   case NonFatal(t) => JwtCreationFailure(t)
                 }
      key     <- decodePrivateKey(factory, credentials.privateKey).adaptError {
                   case NonFatal(t) => InvalidCredentials(t)
                 }
    } yield new TokenProvider[F] {

      import sttp.client3._
      import sttp.client3.circe._

      override def getToken(scope: Scope): F[Token] = {
        for {
          _        <- Logger[F].info(s"Getting fresh access token with scope [$scope]...")
          issuedAt <- Clock[F].realTimeInstant
          expiresAt = issuedAt.plusSeconds(60 * 60)
          jwt      <- createJwt(scope, issuedAt, expiresAt)
          token    <- sttpBackend
                        .send(
                          basicRequest
                            .post(uri"${credentials.tokenUri}")
                            .body(
                              Map(
                                "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                "assertion" -> jwt
                              )
                            )
                            .response(asJson[Json])
                        )
                        .adaptError {
                          case NonFatal(t) => CommunicationError(t)
                        }
                        .onError {
                          case throwable => logger.error(throwable)(s"Failed to get access token for scope [$scope] due to [$throwable]")
                        }
                        .flatMap { response =>
                          response.body match {
                            case Right(json)     =>
                              Sync[F]
                                .fromEither(json.hcursor.downField("access_token").as[String])
                                .adaptError {
                                  case NonFatal(error) =>
                                    UnexpectedResponse(s"Couldn't decode response due to ${error.getMessage}")
                                }
                                .map(token => Token(token, scope, expiresAt))
                                .onError {
                                  case throwable =>
                                    logger.trace(throwable)(s"Failed to get access token for scope [$scope]: [${response.body}]") *>
                                      logger.error(throwable)(s"Failed to get access token for scope [$scope]")
                                }
                            case Left(throwable) =>
                              logger.error(throwable)(
                                s"Failed to get access token for scope [$scope], HTTP status: ${response.code.code}, response: [${response.body}]"
                              ) *>
                                UnexpectedResponse(s"Status: ${response.code.code}").raiseError[F, Token]
                          }
                        }
        } yield token
      }
        .flatTap(_ => logger.info(s"Successfully got access token for scope [$scope]."))
        .onError { case error => logger.error(s"Error while getting access token for scope [$scope]: $error!") }

      private def createJwt(scope: Scope, issuedAt: Instant, expires: Instant): F[String] =
        Sync[F].delay {
          Jwt.encode(
            JwtClaim(
              expiration = Some(expires.getEpochSecond),
              issuedAt = Some(issuedAt.getEpochSecond),
              issuer = Some(credentials.clientEmail),
              audience = Some(Set(credentials.tokenUri.value)),
              content = s"""{"scope": "${scope.value}"}"""
            ),
            key,
            JwtAlgorithm.RS256
          )
        }
    }
  }

}
