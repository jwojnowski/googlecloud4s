package me.wojnowski.googlecloud4s.auth

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

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.time.ZoneId
import java.time.{Clock => JavaClock}
import java.util.Base64
import scala.util.Try

// TODO ES256 support
trait TokenVerifier[F[_]] {
  def verifyIdentityToken(rawToken: String): F[Either[TokenVerifier.Error, Set[TargetAudience]]]

  def verifyAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Either[TokenVerifier.Error, A]]
}

object TokenVerifier {

  def apply[F[_]](implicit ev: TokenVerifier[F]): TokenVerifier[F] = ev

  def default[F[_]: Sync](implicit backend: SttpBackend[F, Any]): TokenVerifier[F] =
    create[F](PublicKeyProvider.jwk[F]())

  def create[F[_]: Sync](
    publicKeyProvider: PublicKeyProvider[F],
    expectedIssuers: Set[String] = Set("https://accounts.google.com")
  ): TokenVerifier[F] =
    new TokenVerifier[F] {
      private val base64Decoder = Base64.getDecoder

      private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)

      override def verifyIdentityToken(rawToken: String): F[Either[TokenVerifier.Error, Set[TargetAudience]]] =
        verifyAndParseToken(rawToken)
          .map { case Result(_, claim, _) => claim.audience.toSet.flatten.map(TargetAudience.apply) }
          .attemptNarrow[TokenVerifier.Error]

      override def verifyAndDecodeIdentityToken[A: Decoder](rawToken: String): F[Either[TokenVerifier.Error, A]] =
        verifyAndParseToken(rawToken)
          .flatMap {
            case Result(_, claim, _) =>
              Sync[F].fromEither(io.circe.parser.decode[A](claim.content).leftMap(TokenVerifier.Error.CouldNotDecodeClaim.apply))
          }
          .attemptNarrow[TokenVerifier.Error]

      private def verifyAndParseToken(rawToken: String): F[Result] =
        for {
          instant   <- Clock[F].realTimeInstant
          javaClock = JavaClock.fixed(instant, ZoneId.of("UTC"))
          kid       <- Sync[F].fromEither(extractKid(rawToken))
          publicKey <- publicKeyProvider.getKey(kid).map(_.leftMap(TokenVerifier.Error.CouldNotFindPublicKey.apply)).rethrow
          result    <- decodeAndVerifyToken(rawToken, javaClock, publicKey)
        } yield result

      private def decodeAndVerifyToken(
        rawToken: String,
        javaClock: JavaClock,
        publicKey: PublicKey
      ): F[Result] =
        Sync[F]
          .fromEither {
            JwtCirce(javaClock)
              .decodeAll(rawToken, publicKey, supportedAlgorithms)
              .toEither
              .map((Result.apply _).tupled)
              .flatTap { case Result(_, claim, _) => ensureExpectedIssuer(claim) }
          }
          .adaptError {
            case jwtException: JwtException => JwtVerificationError(jwtException)
          }

      private def ensureExpectedIssuer(claim: JwtClaim): Either[Error.UnexpectedIssuer, Unit] =
        claim.issuer match {
          case Some(issuer) if expectedIssuers.contains(issuer) => Right(())
          case _                                                => Left(TokenVerifier.Error.UnexpectedIssuer(claim.issuer, expectedIssuers))
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
    case object CouldNotExtractKeyId extends Error

    case class CouldNotFindPublicKey(cause: PublicKeyProvider.Error) extends Error

    case class CouldNotDecodeClaim(cause: io.circe.Error) extends Error

    case class JwtVerificationError(cause: JwtException) extends Error

    case class UnexpectedIssuer(found: Option[String], expected: Set[String]) extends Error
  }

}
