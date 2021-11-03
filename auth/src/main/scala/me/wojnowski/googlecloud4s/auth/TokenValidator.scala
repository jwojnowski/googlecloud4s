package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Sync
import cats.syntax.all._
import pdi.jwt.Jwt
import pdi.jwt.JwtClaim
import sttp.client3.SttpBackend
import sttp.client3.circe.asJsonAlways

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.time.ZoneId
import java.util.Base64
import scala.util.control.NoStackTrace
import scala.util.control.NonFatal

// TODO caching of the certs
trait TokenValidator[F[_]] {
  def validateIdentityToken(rawToken: String): F[Option[TargetAudience]]
}

object TokenValidator {

  def instance[F[_]: Sync](implicit backend: SttpBackend[F, Any]): TokenValidator[F] =
    new TokenValidator[F] {
      private val base64Decoder = Base64.getDecoder

      private val expectedIssuer = "https://accounts.google.com"

      private val certificateFactory = CertificateFactory.getInstance("X.509")

      override def validateIdentityToken(rawToken: String): F[Option[TargetAudience]] =
        validateToken(rawToken).map {
          _.flatMap { claim =>
            io.circe
              .parser
              .parse(claim.content)
              .flatMap(_.hcursor.get[String]("aud"))
              .toOption
              .map(TargetAudience.apply)
          }
        }

      private def validateToken(rawToken: String): F[Option[JwtClaim]] =
        for {
          instant   <- Clock[F].realTimeInstant
          javaClock = java.time.Clock.fixed(instant, ZoneId.of("UTC"))
          kid       <- Sync[F].fromEither(extractKid(rawToken)).adaptError {
                         case NonFatal(_) => Error.CouldNotExtractKid
                       }
          publicKey <- getPublicKey(kid)
        } yield Jwt(javaClock)
          .decode(rawToken, publicKey)
          .toOption
          .filter(_.issuer.forall(_ === expectedIssuer))

      private def getPublicKey(kid: String): F[PublicKey] = {
        import sttp.client3._
        backend
          .send(basicRequest.get(uri"https://www.googleapis.com/oauth2/v1/certs").response(asJsonAlways[Map[String, String]]))
          .flatMap { response =>
            response.body match {
              case Right(map)      =>
                Sync[F].fromOption(map.get(kid), Error.CouldNotFindCertificate(kid))
              case Left(throwable) =>
                Error.CouldNotParseResponse(throwable).raiseError[F, String]
            }
          }
          .flatMap { rawCert =>
            Sync[F].delay(certificateFactory.generateCertificate(new ByteArrayInputStream(rawCert.getBytes)).getPublicKey)
          }
      }

      private def extractKid(rawToken: String): Either[io.circe.Error, String] = {
        val rawJson = new String(base64Decoder.decode(rawToken.takeWhile(_ != '.')), StandardCharsets.UTF_8)
        io.circe
          .parser
          .parse(rawJson)
          .flatMap { json =>
            json.hcursor.get[String]("kid")
          }
      }

    }

  sealed trait Error extends NoStackTrace with Product with Serializable

  object Error {
    case class CouldNotFindCertificate(kid: String) extends Error
    case object CouldNotExtractKid extends Error
    case class CouldNotParseResponse(cause: Throwable) extends Error
  }

}
