package me.wojnowski.googlecloud4s.auth

import cats.effect.IO
import cats.effect.testkit.TestControl
import me.wojnowski.googlecloud4s.TimeUtils.InstantToFiniteDuration

import scala.annotation.unused
import me.wojnowski.googlecloud4s.auth.TokenVerifier.Error.JwtVerificationError
import munit.CatsEffectSuite
import pdi.jwt.exceptions.JwtEmptySignatureException
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.exceptions.JwtValidationException
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.testing.SttpBackendStub

import java.time.Instant

// TODO use PublicKeyProvider.static
class TokenVerifierTest extends CatsEffectSuite {

  private val trueGoogleCerts = """{
                                |  "85828c59284a69b54b27483e487c3bd46cd2a2b3": "-----BEGIN CERTIFICATE-----\nMIIDJjCCAg6gAwIBAgIIGfd3XMYbtS8wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE\nAxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe\nFw0yMTEwMjcxNTIxMzFaFw0yMTExMTMwMzM2MzFaMDYxNDAyBgNVBAMTK2ZlZGVy\nYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDMwfFa7HO0wpd0GG/esiaVS9b/Knqc7Tdx\nVc/IDRXKrT4zst84kkgX4502hEZ2wU8dUoIWijqoaFHysEKYzEDh8z5RuTmoq5So\nI314/fJkmrwLD21Su3+qHu5b6p8yvS6lzJ5IeEL/NbWVMazmyjAOiso3+NDVJ+H2\nF8HF9zxPt1AACASqoxwTe77YnvTWPKEnPSm+6sJ/OKIHdu5jpIZESEFDA3CmdCO3\n+UTZTiSbwVLTCg/O3RxwTfOwYvaoz4tZls6b1peq5Keo0ku3e0ZW9KGd6/4aInVd\n9DcRabR/jDRNf19M6TGy2XS+j+9b8WMZDQPWldKOpvuf6EB1fBz3AgMBAAGjODA2\nMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG\nAQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQCrCMtikm2tKbcghtBvL+DtQ7aYh+a/\nikNwpSKuOjwjevlKJ1us3CXfU8ewbeeNPgEo7J47e/lbo0ax6lwpnWy08XSZlSTj\nb/Mq65bpDK5n+I13SFA4ZBJYtdVyAT8R7XPR6WnPVSo4ZYiJxv74NW3tGQqCG0aL\nIDq314xCJbhooLJ9Is/uEAIURish+1Aybd1P19w1DijSf+Cwr7hS+jRL9jAVs2UC\nJF0/KnUKrhtj7evDajrD/l8owythkYn6su8svmucUALpfRvQSd5kg2KQpChbizYV\nuQBTLBVNQMV4EZ5ppol3vdCUfm+h40ZsUFob2hK6X09sQWN77YLLh46s\n-----END CERTIFICATE-----\n",
                                |  "bbd2ac7c4c5eb8adc8eeffbc8f5a2dd6cf7545e4": "-----BEGIN CERTIFICATE-----\nMIIDJjCCAg6gAwIBAgIIBXF9Zauw0z0wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE\nAxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe\nFw0yMTEwMTkxNTIxMzFaFw0yMTExMDUwMzM2MzFaMDYxNDAyBgNVBAMTK2ZlZGVy\nYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDL3fR20ZN4wbnYg+wqYy4Sk8ctrG5hCV25\nl3oGoVWNzb5QVlHx82q2djMCNtdYkP9OrGB2WP8xbi+iC9fiwfBWlk4293J4agjI\nNC2KzqK4ZCFBqZXzkX8lusRScTDdAI7zsuLPlRcboErYdHI+EN8x7HX+07RE6bJI\nsV0JNLXsPTBqHvUhumLSAjwtSLqTNVGNgMgLWgT6Irdj2zzwGLhkZgnFcBuc3BvB\nWQY4oJeNr2TSOMEZHFkP7goO5/j3eUU44QZIsMDZCn2sOqtRE/XZdiC9//MnH+RI\nOKmevSAvpXPafcfj5c3i0DjnO7NGQegTv5d7E+r62/uRDylKNja5AgMBAAGjODA2\nMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG\nAQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQBvEZ3/XHP40pOEFTMQYK5lQeSwNd6u\ni99+gFwww8Pa1wnbrWMbG4LNztxi1goWKJWVlMQBw94krT6c7dAhVnma8ILGhWUH\nJQFErr/mT6UrFBhr0FCNsb7+HEvfKB1VScePuy2XOBr5jFeCiOYVA97takXNxVIW\nGrWMlwc9C5kIm29153bozx+lGvvK5r29pk4+6ldvnxoV84GA8ioY1dLi3EI5v3cu\n4N95DqpQBkpYA3KZeGjGTSAS4a1D5mQiMkvznadCLANdrFyYanbAzQNhr7glFvZM\ny4jbo2xWW6z9zR0qm8zvPz4/Xy7JrfqfVdN1+3Oh69eBLv1+PmYMGj1o\n-----END CERTIFICATE-----\n"
                                |}""".stripMargin

  private val fakeCerts =
    """{
      |   "11e03f39b8d300c8c9a1b800ddebfcfde4152c0c": "-----BEGIN CERTIFICATE-----\nMIICtjCCAZ4CCQCUE7KeXkP23zANBgkqhkiG9w0BAQUFADAdMRswGQYDVQQDDBJn\nb29nbGVjbG91ZDRzLXRlc3QwHhcNMjIwODE0MTcxODMxWhcNMjMwODE0MTcxODMx\nWjAdMRswGQYDVQQDDBJnb29nbGVjbG91ZDRzLXRlc3QwggEiMA0GCSqGSIb3DQEB\nAQUAA4IBDwAwggEKAoIBAQDt5Y6xd/5tOmr+OCm0AHEITxUurAltkLT8T6l3Ux+c\nonhUIGz7KT2RzSuuGMEGRp1xtLahHXYaK1A0WeNREKzkX24ivIM9sPvTE1jnaPnL\nzJW2REWW3BkIfGZskzs0t96ztMB3YUfesm/S33JJ1h6d+qa8gToGcP8Wmp4aAoEA\n5WRINxSlW1LAf+PuSgqUaeaTfn66yfjn/fVMsK8wgGMK6W4+scJhCWIP9+FxlHjN\n61yH+CkyWzIQiV5+/9GRVKp2HSAl1T6ngVyei8+bj8guWPcAxzGn7HLFq+vBdqSJ\nnOdzRGOa1DzeRYklNvspFLZ9zfhK9njCxwZgVw08PV8TAgMBAAEwDQYJKoZIhvcN\nAQEFBQADggEBAK8QIlPmZmoLtZ3H1Ie836n5omIwh3JYemXKPgc1yv0W3veXE0AF\nxVzsl1ymHFUd0Qw3WbHxxadGw+axHU7KEeAZX7+vxmSOHCRo+swMpKcc7iXWcs5d\n/a9QXxBgocA3RLwp/hfKugwetCX1AKQVRNZU7MzYQiEfncClqsKttGiSKIlzaeVi\nOGnOqpZuXRMsPV/sMj7jFjoFkCnb9RXSejv8AQosfabSYNATMsfWIyCoHKbUP41e\nQrWm8lhQBI1MQGBZoRXuiyqH7YaJSQSHi57z0mojCcRHrduawtYMB2sNIIaJUhIo\nCNTl06s1yM1Y3Q9yzEN7xyRa9BKJThNFXy8=\n-----END CERTIFICATE-----"
      |}""".stripMargin

  val tokenA =
    "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

  val tokenExpiration = Instant.parse("2021-11-02T20:32:16Z")

  private val backendWithTrueGoogleCerts =
    SttpBackendStub.apply[IO, Any](new CatsMonadAsyncError).whenAnyRequest.thenRespond(trueGoogleCerts)

  private implicit val backendWithFakeCerts: SttpBackendStub[IO, Any] =
    SttpBackendStub.apply[IO, Any](new CatsMonadAsyncError).whenAnyRequest.thenRespond(fakeCerts)

  test("Correct IdentityToken signed with RS256") {
    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier.default[IO](implicitly, backendWithTrueGoogleCerts).verifyIdentityToken(tokenA).map { result =>
        assertEquals(result, Right(Set(TargetAudience("http://example.com"))))
      }
    }
  }

  test("Expired IdentityToken") {
    runAtInstant(tokenExpiration.plusSeconds(3)) {
      TokenVerifier.default[IO](implicitly, backendWithTrueGoogleCerts).verifyIdentityToken(tokenA).map {
        case Left(JwtVerificationError(_: JwtExpirationException)) => ()
        case _                                                     => fail("expected JwtExpirationException")
      }
    }
  }

  test("Incorrect issuer") {
    val tokenWithOtherIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE3MjdiNmI0OTQwMmI5Y2Y5NWJlNGU4ZmQzOGFhN2U3YzExNjQ0YjEiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2Nsb3VkdGFza3MuZ29vZ2xlYXBpcy5jb20vdjIvcHJvamVjdHMvZ2Nsb3VkLWRldmVsL2xvY2F0aW9ucyIsImF6cCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6InN0aW0tdGVzdEBzdGVsbGFyLWRheS0yNTQyMjIuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjoxNjYwODgwNjczLCJpYXQiOjE2NjA4NzcwNzMsImlzcyI6Imh0dHBzOi8vYWNjb3VudHMuZ29vZ2xlLmNvbSIsInN1YiI6IjExMjgxMDY3Mjk2MzcyODM2NjQwNiJ9"

    runAtInstant(tokenExpiration.minusSeconds(3)) {
      TokenVerifier
        .default[IO]
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
        .default[IO](implicitly, backendWithTrueGoogleCerts)
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
        .create[IO](Set("https://thisisnotgoogle.com"), publicKeyProvider = PublicKeyProvider.googleV1[IO]())
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
        .default[IO]
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
        .default[IO]
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
        .default[IO]
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
        .default[IO]
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
