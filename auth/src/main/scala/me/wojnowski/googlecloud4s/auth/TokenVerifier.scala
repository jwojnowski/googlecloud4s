package me.wojnowski.googlecloud4s.auth

import cats.data.EitherT
import cats.effect.Clock
import cats.effect.Sync
import cats.syntax.all._
import io.circe.Decoder
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.auth.TokenVerifier.Error.CouldNotExtractKeyId
import me.wojnowski.googlecloud4s.auth.TokenVerifier.Error.JwtVerificationError
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtCirce
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.exceptions.JwtException
import sttp.client3.SttpBackend
import sttp.client3.circe.asJsonAlways
import sttp.model.Uri

import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.time.ZoneId
import java.time.{Clock => JavaClock}
import sttp.model.Uri._

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

import CatsExtensions._

// TODO caching of the certs?
// TODO ES256 support
// TODO https://www.googleapis.com/oauth2/v3/certs format support
trait TokenVerifier[F[_]] {
  def verifyIdentityToken(rawToken: String): F[Either[TokenVerifier.Error, Set[TargetAudience]]]

  def verifyAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Either[TokenVerifier.Error, A]]
}

object TokenVerifier {

  def apply[F[_]](implicit ev: TokenVerifier[F]): TokenVerifier[F] = ev

  def default[F[_]: Sync](implicit backend: SttpBackend[F, Any]): TokenVerifier[F] =
    create[F](
      expectedIssuers = Set("https://accounts.google.com"),
      certificatesLocation = uri"https://www.googleapis.com/oauth2/v1/certs"
    )

  def create[F[_]: Sync](
    expectedIssuers: Set[String],
    certificatesLocation: Uri
  )(
    implicit backend: SttpBackend[F, Any]
  ): TokenVerifier[F] =
    new TokenVerifier[F] {
      private val base64Decoder = Base64.getDecoder

      private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)

      private val certificateFactory = CertificateFactory.getInstance("X.509")

      override def verifyIdentityToken(rawToken: String): F[Either[TokenVerifier.Error, Set[TargetAudience]]] =
        verifyAndParseToken(rawToken).map(_.map { case Result(_, claim, _) => claim.audience.toSet.flatten.map(TargetAudience.apply) })

      override def verifyAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Either[TokenVerifier.Error, A]] = {
        import io.circe.syntax._

        verifyAndParseToken(rawToken).map {
          _.flatMap {
            case Result(_, _, content) => content.asJson.as[A].leftMap(TokenVerifier.Error.CouldNotDecodeClaim.apply)
          }
        }
      }

      private def verifyAndParseToken(rawToken: String): F[Either[TokenVerifier.Error, Result]] = {
        for {
          instant   <- EitherT.liftF(Clock[F].realTimeInstant)
          javaClock = JavaClock.fixed(instant, ZoneId.of("UTC"))
          kid       <- EitherT.fromEither(extractKid(rawToken))
          publicKey <- EitherT.liftF(getPublicKey(kid))
          result    <- EitherT(decodeAndVerifyToken(rawToken, javaClock, publicKey))
        } yield result
      }.value

      private def decodeAndVerifyToken(
        rawToken: String,
        javaClock: JavaClock,
        publicKey: PublicKey
      ): F[Either[TokenVerifier.Error, Result]] =
        JwtCirce(javaClock)
          .decodeAll(rawToken, publicKey, supportedAlgorithms)
          .toEither
          .map((Result.apply _).tupled)
          .flatTap { case Result(_, claim, _) => ensureExpectedIssuer(claim) }
          .pure[F]
          .rethrowSome {
            case jwtException: JwtException => JwtVerificationError(jwtException)
            case error: TokenVerifier.Error => error
          }

      private def ensureExpectedIssuer(claim: JwtClaim): Either[Error.UnexpectedIssuer, Unit] =
        claim.issuer match {
          case Some(issuer) if expectedIssuers.contains(issuer) => Right(())
          case _                                                => Left(TokenVerifier.Error.UnexpectedIssuer(claim.issuer, expectedIssuers))
        }

      private def getPublicKey(kid: String): F[PublicKey] = {
        import sttp.client3._
        backend
          .send(basicRequest.get(certificatesLocation).response(asJsonAlways[Map[String, String]]))
          .flatMap { response =>
            response.body match {
              case Right(map)      =>
                Sync[F].fromOption(map.get(kid), Error.CouldNotFindCertificate(kid))
              case Left(throwable) =>
                Error.CouldNotParseCertificateResponse(throwable).raiseError[F, String]
            }
          }
          .flatMap { rawCert =>
            Sync[F].delay(certificateFactory.generateCertificate(new ByteArrayInputStream(rawCert.getBytes)).getPublicKey)
          }
      }

      private def extractKid(rawToken: String): Either[CouldNotExtractKeyId.type, String] =
        Try {
          JwtCirce
            .parseHeader(
              new String(
                base64Decoder.decode(rawToken.takeWhile(_ != '.')),
                StandardCharsets.UTF_8
              )
            )
        }
          .toOption
          .flatMap(_.keyId)
          .toRight(CouldNotExtractKeyId)

    }

  private case class Result(header: JwtHeader, claim: JwtClaim, raw: String)

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class CouldNotFindCertificate(kid: String) extends Error

    case object CouldNotExtractKeyId extends Error

    case class CouldNotParseCertificateResponse(cause: Throwable) extends Error

    case class CouldNotDecodeClaim(cause: io.circe.Error) extends Error

    case class JwtVerificationError(cause: JwtException) extends Error

    case class UnexpectedIssuer(found: Option[String], expected: Set[String]) extends Error
  }

}
