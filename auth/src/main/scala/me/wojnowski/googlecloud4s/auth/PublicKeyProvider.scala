package me.wojnowski.googlecloud4s.auth

import cats.effect.Ref
import cats.effect.kernel.Sync
import sttp.client3.SttpBackend
import sttp.client3.circe._
import sttp.model.Uri
import cats.syntax.all._
import io.circe.Decoder
import me.wojnowski.googlecloud4s.ProductSerializableNoStacktrace
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.Error.CouldNotDecodeKey
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.Error.CouldNotFindPublicKey
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.KeyId
import sttp.client3._

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import scala.util.Try

trait PublicKeyProvider[F[_]] {
  def getKey(keyId: KeyId): F[Either[PublicKeyProvider.Error, PublicKey]]

  def getAllKeys: F[Either[PublicKeyProvider.Error, Map[KeyId, Either[PublicKeyProvider.Error, PublicKey]]]]
}

object PublicKeyProvider {

  type KeyId = String

  private val certificateFactory = CertificateFactory.getInstance("X.509")

  private val rsaKeyFactory = KeyFactory.getInstance("RSA")

  def cached[F[_]: Sync](delegate: PublicKeyProvider[F]): F[PublicKeyProvider[F]] =
    Ref[F].of(Map.empty[KeyId, PublicKey]).map { keyRef =>
      new PublicKeyProvider[F] {
        override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
          for {
            maybeCachedKey <- keyRef.get.map(_.get(keyId))
            keyEither      <- maybeCachedKey.fold(refreshAndGet(keyId))(_.asRight[Error].pure[F])
          } yield keyEither

        override def getAllKeys: F[Either[Error, Map[KeyId, Either[Error, PublicKey]]]] = delegate.getAllKeys

        private def refreshAndGet(keyId: KeyId): F[Either[Error, PublicKey]] =
          delegate.getAllKeys.flatMap {
            case Right(keys) =>
              keyRef.modify { _ =>
                val newKeys = keys.collect {
                  case (keyId, Right(key)) => keyId -> key
                }
                (newKeys, newKeys.get(keyId).toRight(Error.CouldNotFindPublicKey(keyId)))
              }
            case Left(error) =>
              Sync[F].raiseError(error)
          }
      }
    }

  def googleV1[F[_]: Sync](
    certificatesLocation: Uri = uri"https://www.googleapis.com/oauth2/v1/certs"
  )(
    implicit backend: SttpBackend[F, Any]
  ): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {

      override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
        getAllKeys.map(_.flatMap(_.get(keyId).toRight(CouldNotFindPublicKey(keyId)).flatten))

      override def getAllKeys: F[Either[Error, Map[KeyId, Either[Error, PublicKey]]]] =
        backend
          .send(basicRequest.get(certificatesLocation).response(asJson[Map[String, String]]))
          .map { response =>
            response.body match {
              case Right(map)      =>
                map.fmap(fromX509CertificateString).asRight[Error]
              case Left(throwable) =>
                Error.CouldNotParseResponse(throwable).asLeft[Map[KeyId, Either[Error, PublicKey]]]
            }
          }

      private def fromX509CertificateString(rawCertificate: String): Either[Error, PublicKey] =
        Try(certificateFactory.generateCertificate(new ByteArrayInputStream(rawCertificate.getBytes)).getPublicKey)
          .toEither
          .leftMap(CouldNotDecodeKey.apply)

    }

  def jwk[F[_]: Sync](
    certificatesLocation: Uri = uri"https://www.googleapis.com/oauth2/v3/certs"
  )(
    implicit backend: SttpBackend[F, Any]
  ): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {

      override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
        getAllKeys.map(_.flatMap(_.get(keyId).toRight(CouldNotFindPublicKey(keyId)).flatten))

      override def getAllKeys: F[Either[Error, Map[KeyId, Either[Error, PublicKey]]]] =
        backend
          .send(basicRequest.get(certificatesLocation).response(asJson[JsonWebKeySet]))
          .flatMap { response =>
            response.body match {
              case Right(JsonWebKeySet(keys)) =>
                keys.map(key => (key.keyId, key.toPublicKey.leftWiden[Error])).toMap.pure[F]

              case Left(throwable)            =>
                Error.CouldNotParseResponse(throwable).raiseError[F, Map[KeyId, Either[Error, PublicKey]]]
            }
          }
          .attemptNarrow[Error]

    }

  private case class JsonWebKeySet(keys: List[JsonWebKey])

  private object JsonWebKeySet {
    implicit val decoder: Decoder[JsonWebKeySet] = Decoder.forProduct1("keys")(JsonWebKeySet.apply)
  }

  private case class JsonWebKey(modulus: String, publicExponent: String, keyId: String) {

    def toPublicKey: Either[Error.CouldNotDecodeKey, PublicKey] =
      Try {
        val keySpec = new RSAPublicKeySpec(
          new BigInteger(1, Base64.getUrlDecoder.decode(modulus)),
          new BigInteger(1, Base64.getUrlDecoder.decode(publicExponent))
        )

        rsaKeyFactory.generatePublic(keySpec)
      }.toEither.leftMap(Error.CouldNotDecodeKey.apply)

  }

  private object JsonWebKey {
    implicit val decoder: Decoder[JsonWebKey] = Decoder.forProduct3("n", "e", "kid")(JsonWebKey.apply)
  }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class CouldNotFindPublicKey(keyId: KeyId) extends Error

    case class CouldNotDecodeKey(cause: Throwable) extends Error

    case class CouldNotParseResponse(cause: Throwable) extends Error
  }

}
