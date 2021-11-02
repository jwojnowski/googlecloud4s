package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Sync
import io.circe.Json
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error.CommunicationError
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error.InvalidCredentials
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error.JwtCreationFailure
import me.wojnowski.googlecloud4s.auth.TokenProvider.Error.UnexpectedResponse
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import cats.syntax.all._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Uri
import sttp.client3.SttpBackend
import io.circe.syntax._

object CredentialsTokenProvider {

  def instance[F[_]: Sync](credentials: Credentials)(implicit sttpBackend: SttpBackend[F, Any]): F[TokenProvider[F]] = {
    implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

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

      override def getIdentityToken(audience: TargetAudience): F[IdentityToken] = {
        for {
          _        <- Logger[F].debug(s"Getting fresh identity token for audience [$audience]...")
          issuedAt <- Clock[F].realTimeInstant
          expiresAt = issuedAt.plusSeconds(60 * 60)
          token    <-
            sttpBackend
              .send(
                createRequest(credentials.tokenUri, issuedAt, expiresAt, Map("target_audience" -> audience.value.toList.mkString(" ")))
              )
              .adaptError {
                case NonFatal(t) => CommunicationError(t)
              }
              .flatMap { response =>
                response.body match {
                  case Right(json)     =>
                    Sync[F]
                      .fromEither(json.hcursor.downField("id_token").as[String])
                      .adaptError {
                        case NonFatal(error) =>
                          UnexpectedResponse(s"Couldn't decode response due to ${error.getMessage}")
                      }
                      .map { token =>
                        IdentityToken(token, audience, expiresAt)
                      }
                  case Left(throwable) =>
                    logger.error(throwable)(
                      s"Failed to get identity token for audience [$audience] based on credentials, HTTP status: ${response.code.code}, response: [${response.body}]"
                    ) *>
                      UnexpectedResponse(s"Status: ${response.code.code}").raiseError[F, IdentityToken]
                }
              }
        } yield token
      }
        .flatTap { _ =>
          logger.info(s"Successfully got identity token for audience [$audience] based on credentials.")
        }
        .onError {
          case throwable =>
            logger.error(throwable)(s"Failed to get identity token for audience [$audience] based on credentials.")
        }

      override def getAccessToken(scopes: Scopes): F[AccessToken] = {
        for {
          _        <- Logger[F].debug(s"Getting fresh access token with scopes [$scopes]...")
          issuedAt <- Clock[F].realTimeInstant
          expiresAt = issuedAt.plusSeconds(60 * 60)
          token    <- sttpBackend
                        .send(
                          createRequest(credentials.tokenUri, issuedAt, expiresAt, Map("scopes" -> scopes.values.toList.mkString(" ")))
                        )
                        .adaptError {
                          case NonFatal(t) => CommunicationError(t)
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
                                .map { token =>
                                  AccessToken(token, scopes, expiresAt)
                                }
                            case Left(throwable) =>
                              logger.error(throwable)(
                                s"Failed to get access token for scope [$scopes] based on credentials, HTTP status: ${response.code.code}, response: [${response.body}]"
                              ) *>
                                UnexpectedResponse(s"Status: ${response.code.code}").raiseError[F, AccessToken]
                          }
                        }
        } yield token
      }
        .flatTap { _ =>
          logger.info(s"Successfully got access token for scope [$scopes] based on credentials.")
        }
        .onError {
          case throwable =>
            logger.error(throwable)(s"Failed to get access token for scope [$scopes] based on credentials.")
        }

      private def createRequest(
        tokenUri: String Refined Uri,
        issuedAt: Instant,
        expires: Instant,
        additionalData: Map[String, String]
      ): RequestT[Identity, Either[ResponseException[String, io.circe.Error], Json], Any] =
        basicRequest
          .post(uri"$tokenUri")
          .body(
            Map(
              "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
              "assertion" -> Jwt.encode(
                JwtClaim(
                  expiration = Some(expires.getEpochSecond),
                  issuedAt = Some(issuedAt.getEpochSecond),
                  issuer = Some(credentials.clientEmail),
                  audience = Some(Set(credentials.tokenUri.value)),
                  content = additionalData.asJson.noSpaces
                ),
                key,
                JwtAlgorithm.RS256
              )
            )
          )
          .response(asJson[Json])

    }
  }

  private def decodePrivateKey[F[_]: Sync](keyFactory: KeyFactory, rawKey: String): F[RSAPrivateKey] =
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

}
