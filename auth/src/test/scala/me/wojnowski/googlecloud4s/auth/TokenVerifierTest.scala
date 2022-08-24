package me.wojnowski.googlecloud4s.auth

import cats.Applicative
import cats.effect.IO
import cats.effect.testkit.TestControl
import me.wojnowski.googlecloud4s.TimeUtils.InstantToFiniteDuration

import scala.annotation.unused
import me.wojnowski.googlecloud4s.auth.TokenVerifier.Error.JwtVerificationError
import munit.CatsEffectSuite
import pdi.jwt.exceptions.JwtEmptySignatureException
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.exceptions.JwtValidationException

import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import cats.syntax.all._
import io.circe.Json
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.Error
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.KeyId
import me.wojnowski.googlecloud4s.auth.TokenVerifierTest.staticKeyProvider

import java.security.KeyFactory
import java.security.PublicKey

class TokenVerifierTest extends CatsEffectSuite {

  private val keyFactory = KeyFactory.getInstance("RSA")

  private val trueGoogleKeys: Map[KeyId, PublicKey] =
    Map(
      "85828c59284a69b54b27483e487c3bd46cd2a2b3" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzMHxWuxztMKXdBhv3rImlUvW/yp6nO03cVXPyA0Vyq0+M7LfOJJIF+OdNoRGdsFPHVKCFoo6qGhR8rBCmMxA4fM+Ubk5qKuUqCN9eP3yZJq8Cw9tUrt/qh7uW+qfMr0upcyeSHhC/zW1lTGs5sowDorKN/jQ1Sfh9hfBxfc8T7dQAAgEqqMcE3u+2J701jyhJz0pvurCfziiB3buY6SGREhBQwNwpnQjt/lE2U4km8FS0woPzt0ccE3zsGL2qM+LWZbOm9aXquSnqNJLt3tGVvShnev+GiJ1XfQ3EWm0f4w0TX9fTOkxstl0vo/vW/FjGQ0D1pXSjqb7n+hAdXwc9wIDAQAB",
      "bbd2ac7c4c5eb8adc8eeffbc8f5a2dd6cf7545e4" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy930dtGTeMG52IPsKmMuEpPHLaxuYQlduZd6BqFVjc2+UFZR8fNqtnYzAjbXWJD/Tqxgdlj/MW4vogvX4sHwVpZONvdyeGoIyDQtis6iuGQhQamV85F/JbrEUnEw3QCO87Liz5UXG6BK2HRyPhDfMex1/tO0ROmySLFdCTS17D0wah71Ibpi0gI8LUi6kzVRjYDIC1oE+iK3Y9s88Bi4ZGYJxXAbnNwbwVkGOKCXja9k0jjBGRxZD+4KDuf493lFOOEGSLDA2Qp9rDqrURP12XYgvf/zJx/kSDipnr0gL6Vz2n3H4+XN4tA45zuzRkHoE7+XexPq+tv7kQ8pSjY2uQIDAQAB"
    )
      .fmap(encodedString => new X509EncodedKeySpec(Base64.getDecoder.decode(encodedString)))
      .fmap(keyFactory.generatePublic)

  private val fakeGoogleKeys: Map[KeyId, PublicKey] =
    Map(
      "11e03f39b8d300c8c9a1b800ddebfcfde4152c0c" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7eWOsXf+bTpq/jgptABxCE8VLqwJbZC0/E+pd1MfnKJ4VCBs+yk9kc0rrhjBBkadcbS2oR12GitQNFnjURCs5F9uIryDPbD70xNY52j5y8yVtkRFltwZCHxmbJM7NLfes7TAd2FH3rJv0t9ySdYenfqmvIE6BnD/FpqeGgKBAOVkSDcUpVtSwH/j7koKlGnmk35+usn45/31TLCvMIBjCuluPrHCYQliD/fhcZR4zetch/gpMlsyEIlefv/RkVSqdh0gJdU+p4FcnovPm4/ILlj3AMcxp+xyxavrwXakiZznc0RjmtQ83kWJJTb7KRS2fc34SvZ4wscGYFcNPD1fEwIDAQAB"
    )
      .fmap(encodedString => new X509EncodedKeySpec(Base64.getDecoder.decode(encodedString)))
      .fmap(keyFactory.generatePublic)

  val tokenA =
    "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

  val tokenExpiration = Instant.parse("2021-11-02T20:32:16Z")

  test("Correct IdentityToken signed with RS256") {
    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(trueGoogleKeys))
        .verifyIdentityToken(tokenA)
        .map { result =>
          assertEquals(result, Right(Set(TargetAudience("http://example.com"))))
        }
    }
  }

  test("Expired IdentityToken") {
    runAtInstant(tokenExpiration.plusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(trueGoogleKeys))
        .verifyIdentityToken(tokenA)
        .map {
          case Left(JwtVerificationError(_: JwtExpirationException)) => ()
          case _                                                     => fail("expected JwtExpirationException")
        }
    }
  }

  test("Claim decoding") {
    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(trueGoogleKeys))
        .verifyAndDecodeIdentityToken[JsonObject](tokenA)
        .map { result =>
          assertEquals(result.map(_.apply("email_verified")), Right(Some(Json.True)))
        }
    }
  }

  test("Incorrect issuer") {
    val tokenWithOtherIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys))
        .verifyIdentityToken(tokenWithOtherIssuer)
        .map {
          case Left(TokenVerifier.Error.UnexpectedIssuer(Some("https://thisisnotgoogle.com"), issuers)) =>
            assertEquals(issuers, Set("https://accounts.google.com"))
          case e                                                                                        =>
            fail(s"expected UnexpectedIssuer, got $e")
        }
    }
  }

  test("Public key mismatch") {
    val tokenSignedWithOtherKey =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(trueGoogleKeys))
        .verifyIdentityToken(tokenSignedWithOtherKey)
        .map {
          case Left(JwtVerificationError(_: JwtValidationException)) => ()
          case e                                                     => fail(s"expected JwtValidationException, got $e")
        }
    }
  }

  test("Overridden issuer") {
    val tokenWithOtherIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys), expectedIssuers = Set("https://thisisnotgoogle.com"))
        .verifyIdentityToken(tokenWithOtherIssuer)
        .map { result =>
          assertEquals(result, Right(Set(TargetAudience("http://example.com"))))
        }
    }

  }

  test("Non-existent issuer") {
    val tokenWithoutIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys))
        .verifyIdentityToken(tokenWithoutIssuer)
        .map {
          case Left(TokenVerifier.Error.UnexpectedIssuer(None, issuers)) => assertEquals(issuers, Set("https://accounts.google.com"))
          case e                                                         => fail(s"expected JwtEmptySignatureException, got $e")
        }
    }
  }

  test("Algorithm: none and empty signature") {
    val tokenWithAlgorithmNone =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys))
        .verifyIdentityToken(tokenWithAlgorithmNone)
        .map {
          case Left(JwtVerificationError(_: JwtEmptySignatureException)) => ()
          case e                                                         => fail(s"expected JwtEmptySignatureException, got $e")
        }
    }
  }

  test("Algorithm: none and random signature") {
    val tokenWithAlgorithmNone =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9UZ48cJNaYsnp4S1VKQ_6z2pqRw-iH9Jeniq0ZNZwYL_O7w6wEdGqoT5bVIpuZLE8Y5oznpqxrCWoXC1UZ2EkTiv9gUchbOYHv3TMlAqus8WJfVaXE8OZh5JwaXCJARJ0c51RtLaNu4ariXk5wJfFM3r5d18Q8_jbFi4P5_MBt4pK5GWRN-guaiXq2LigTYqfPCzuTr6ST-iv294dKwPhvfQL2IE03IueS46FSQQbRJ4YFkv1x9-Ebuq1jlF1ggagB4XBxNrgO8eOoXB7oUlcAq92o5NPRcS_21dZgrSepovjopKiIMad6QX7qfbblE7RqFtXUqU93n--aKMsD6xXMw"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys))
        .verifyIdentityToken(tokenWithAlgorithmNone)
        .map {
          case Left(JwtVerificationError(_: JwtValidationException)) => ()
          case e                                                     => fail(s"expected JwtValidationException, got $e")
        }
    }
  }

  test("Unsupported algorithm (HS256)") {
    val tokenWithHs256Algorithm =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .create[IO](staticKeyProvider(fakeGoogleKeys))
        .verifyIdentityToken(tokenWithHs256Algorithm)
        .map {
          case Left(JwtVerificationError(_: JwtValidationException)) => ()
          case e                                                     => fail(s"expected JwtValidationException, got $e")
        }
    }
  }

  def runAtInstant[A](@unused instant: Instant)(program: IO[A]): IO[A] =
    TestControl.executeEmbed(IO.sleep(instant.toFiniteDuration) *> program)

}

object TokenVerifierTest {

  def staticKeyProvider[F[_]: Applicative](keys: Map[KeyId, PublicKey]): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {
      override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
        keys.get(keyId).toRight(Error.CouldNotFindPublicKey(keyId)).leftWiden[Error].pure[F]

      override def getAllKeys: F[Either[Error, Map[KeyId, Either[Error, PublicKey]]]] =
        keys.fmap(_.asRight[Error]).asRight[Error].pure[F]
    }

}
