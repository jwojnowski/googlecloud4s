package me.wojnowski.googlecloud4s.auth

import cats.data.OptionT
import cats.effect.Clock
import cats.effect.Sync
import cats.syntax.all._
import io.circe.Decoder
import io.circe.HCursor
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

// TODO caching of the certs
trait TokenValidator[F[_]] {
  def validateIdentityToken(rawToken: String): F[Option[TargetAudience]]

  def validateAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Option[Either[io.circe.Error, A]]]
}

object TokenValidator {

  def apply[F[_]](implicit ev: TokenValidator[F]): TokenValidator[F] = ev

  def instance[F[_]: Sync](implicit backend: SttpBackend[F, Any]): TokenValidator[F] =
    new TokenValidator[F] {
      private val base64Decoder = Base64.getDecoder

      private val expectedIssuer = "https://accounts.google.com"

      private val certificateFactory = CertificateFactory.getInstance("X.509")

      override def validateIdentityToken(rawToken: String): F[Option[TargetAudience]] =
        validateAndDecodeIdentityToken[HCursor](rawToken).map {
          _.flatMap(_.toOption).flatMap { cursor =>
            cursor
              .get[String]("aud")
              .toOption
              .map(TargetAudience.apply)
          }
        }

      override def validateAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Option[Either[io.circe.Error, A]]] =
        validateToken(rawToken).map(_.map(claim => io.circe.parser.decode[A](claim.content)))

      private def validateToken(rawToken: String): F[Option[JwtClaim]] = {
        for {
          instant   <- OptionT.liftF(Clock[F].realTimeInstant)
          javaClock = java.time.Clock.fixed(instant, ZoneId.of("UTC"))
          kid       <- OptionT.fromOption(extractKid(rawToken).toOption)
          publicKey <- OptionT.liftF(getPublicKey(kid))
          claim     <- OptionT.fromOption(
                         Jwt(javaClock)
                           .decode(rawToken, publicKey)
                           .toOption
                       )
        } yield claim
      }.filter(_.issuer.forall(_ === expectedIssuer)).value

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
