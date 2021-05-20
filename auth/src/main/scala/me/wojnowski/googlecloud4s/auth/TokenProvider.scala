package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Sync
import io.circe.Json
import io.circe.parser.decode
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error._

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.util.control.NoStackTrace
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.syntax.EncoderOps
import io.jsonwebtoken.Jwts
import sttp.client3.SttpBackend

import scala.util.control.NonFatal
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

trait TokenProvider[F[_]] {
  def getToken(scope: Scope): F[Token]
}

object TokenProvider {
  implicit def apply[F[_]](implicit ev: TokenProvider[F]): TokenProvider[F] = ev

  sealed trait Error extends NoStackTrace with Product with Serializable

  object Error {
    case class JwtCreationFailure(cause: Throwable) extends Error
    case class UnexpectedResponse(details: String) extends Error
    case class CommunicationError(cause: Throwable) extends Error
    case object AlgorithmNotFound extends Error
    case class InvalidCredentials(cause: Throwable) extends Error
  }

  def instance[F[_]: Clock: Sync](credentials: Credentials)(implicit sttpBackend: SttpBackend[F, Any]): F[TokenProvider[F]] = {
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

      import java.util.Date
      import sttp.client3._
      import sttp.client3.circe._

      override def getToken(scope: Scope): F[Token] = {
        for {
          _        <- Logger[F].info(s"Getting fresh access token with scope [$scope]...")
          issuedAt <- Clock[F].realTime(TimeUnit.MILLISECONDS).map(Instant.ofEpochMilli)
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
                        .flatMap { response =>
                          response.body match {
                            case Right(json) =>
                              Sync[F]
                                .fromEither(json.hcursor.downField("access_token").as[String])
                                .adaptError {
                                  case NonFatal(error) => UnexpectedResponse(s"Couldn't decode response due to ${error.getMessage}")
                                }
                                .map(token => Token(token, scope, expiresAt))
                            case Left(_)     =>
                              UnexpectedResponse(s"Status: ${response.code.code}").raiseError[F, Token]
                          }
                        }
        } yield token
      }
        .flatTap(_ => logger.info(s"Successfully got access token for scope [$scope]."))
        .onError { case error => logger.error(s"Error while getting access token for scope [$scope] : $error!") }

      private def createJwt(scope: Scope, issuedAt: Instant, expires: Instant): F[String] =
        Sync[F].delay {
          Jwts
            .builder()
            .setIssuer(credentials.clientEmail)
            .setAudience(credentials.tokenUri.value)
            .setIssuedAt(Date.from(issuedAt))
            .setExpiration(Date.from(expires))
            .claim("scope", scope.value)
            .signWith(key)
            .serializeToJsonWith(serializer)
            .compact()
        }

      def serializer(map: JMap[String, _]): Array[Byte] =
        map
          .asScala
          .map {
            case (field, value: String) => field -> value.asJson
            case (field, value: Long)   => field -> value.asJson
            case (field, _)             => throw new IllegalArgumentException(s"Couldn't serialize field [$field]")
          }
          .toMap
          .asJson
          .noSpaces
          .getBytes
    }
  }

}
