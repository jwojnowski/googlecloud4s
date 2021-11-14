package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.IO
import munit.CatsEffectSuite
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.testing.SttpBackendStub
import sttp.client3.SttpBackend

import java.time.Instant

// TODO Test for a wrong issuer
class TokenValidatorTest extends CatsEffectSuite {

  private val trueGoogleCerts = """{
                                |  "85828c59284a69b54b27483e487c3bd46cd2a2b3": "-----BEGIN CERTIFICATE-----\nMIIDJjCCAg6gAwIBAgIIGfd3XMYbtS8wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE\nAxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe\nFw0yMTEwMjcxNTIxMzFaFw0yMTExMTMwMzM2MzFaMDYxNDAyBgNVBAMTK2ZlZGVy\nYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDMwfFa7HO0wpd0GG/esiaVS9b/Knqc7Tdx\nVc/IDRXKrT4zst84kkgX4502hEZ2wU8dUoIWijqoaFHysEKYzEDh8z5RuTmoq5So\nI314/fJkmrwLD21Su3+qHu5b6p8yvS6lzJ5IeEL/NbWVMazmyjAOiso3+NDVJ+H2\nF8HF9zxPt1AACASqoxwTe77YnvTWPKEnPSm+6sJ/OKIHdu5jpIZESEFDA3CmdCO3\n+UTZTiSbwVLTCg/O3RxwTfOwYvaoz4tZls6b1peq5Keo0ku3e0ZW9KGd6/4aInVd\n9DcRabR/jDRNf19M6TGy2XS+j+9b8WMZDQPWldKOpvuf6EB1fBz3AgMBAAGjODA2\nMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG\nAQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQCrCMtikm2tKbcghtBvL+DtQ7aYh+a/\nikNwpSKuOjwjevlKJ1us3CXfU8ewbeeNPgEo7J47e/lbo0ax6lwpnWy08XSZlSTj\nb/Mq65bpDK5n+I13SFA4ZBJYtdVyAT8R7XPR6WnPVSo4ZYiJxv74NW3tGQqCG0aL\nIDq314xCJbhooLJ9Is/uEAIURish+1Aybd1P19w1DijSf+Cwr7hS+jRL9jAVs2UC\nJF0/KnUKrhtj7evDajrD/l8owythkYn6su8svmucUALpfRvQSd5kg2KQpChbizYV\nuQBTLBVNQMV4EZ5ppol3vdCUfm+h40ZsUFob2hK6X09sQWN77YLLh46s\n-----END CERTIFICATE-----\n",
                                |  "bbd2ac7c4c5eb8adc8eeffbc8f5a2dd6cf7545e4": "-----BEGIN CERTIFICATE-----\nMIIDJjCCAg6gAwIBAgIIBXF9Zauw0z0wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE\nAxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe\nFw0yMTEwMTkxNTIxMzFaFw0yMTExMDUwMzM2MzFaMDYxNDAyBgNVBAMTK2ZlZGVy\nYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDL3fR20ZN4wbnYg+wqYy4Sk8ctrG5hCV25\nl3oGoVWNzb5QVlHx82q2djMCNtdYkP9OrGB2WP8xbi+iC9fiwfBWlk4293J4agjI\nNC2KzqK4ZCFBqZXzkX8lusRScTDdAI7zsuLPlRcboErYdHI+EN8x7HX+07RE6bJI\nsV0JNLXsPTBqHvUhumLSAjwtSLqTNVGNgMgLWgT6Irdj2zzwGLhkZgnFcBuc3BvB\nWQY4oJeNr2TSOMEZHFkP7goO5/j3eUU44QZIsMDZCn2sOqtRE/XZdiC9//MnH+RI\nOKmevSAvpXPafcfj5c3i0DjnO7NGQegTv5d7E+r62/uRDylKNja5AgMBAAGjODA2\nMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG\nAQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQBvEZ3/XHP40pOEFTMQYK5lQeSwNd6u\ni99+gFwww8Pa1wnbrWMbG4LNztxi1goWKJWVlMQBw94krT6c7dAhVnma8ILGhWUH\nJQFErr/mT6UrFBhr0FCNsb7+HEvfKB1VScePuy2XOBr5jFeCiOYVA97takXNxVIW\nGrWMlwc9C5kIm29153bozx+lGvvK5r29pk4+6ldvnxoV84GA8ioY1dLi3EI5v3cu\n4N95DqpQBkpYA3KZeGjGTSAS4a1D5mQiMkvznadCLANdrFyYanbAzQNhr7glFvZM\ny4jbo2xWW6z9zR0qm8zvPz4/Xy7JrfqfVdN1+3Oh69eBLv1+PmYMGj1o\n-----END CERTIFICATE-----\n"
                                |}""".stripMargin

  val tokenA =
    "eyJhbGciOiJSUzI1NiIsImtpZCI6Ijg1ODI4YzU5Mjg0YTY5YjU0YjI3NDgzZTQ4N2MzYmQ0NmNkMmEyYjMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwOi8vZXhhbXBsZS5jb20iLCJhenAiOiIxMTA3MTI1OTk3NDM3ODI2NDE4NDciLCJlbWFpbCI6IjI4MzI5NTAxMjEyOC1jb21wdXRlQGRldmVsb3Blci5nc2VydmljZWFjY291bnQuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsImV4cCI6MTYzNTg4NTEzNiwiaWF0IjoxNjM1ODgxNTM2LCJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJzdWIiOiIxMTA3MTI1OTk3NDM3ODI2NDE4NDcifQ.UZ48cJNaYsnp4S1VKQ_6z2pqRw-iH9Jeniq0ZNZwYL_O7w6wEdGqoT5bVIpuZLE8Y5oznpqxrCWoXC1UZ2EkTiv9gUchbOYHv3TMlAqus8WJfVaXE8OZh5JwaXCJARJ0c51RtLaNu4ariXk5wJfFM3r5d18Q8_jbFi4P5_MBt4pK5GWRN-guaiXq2LigTYqfPCzuTr6ST-iv294dKwPhvfQL2IE03IueS46FSQQbRJ4YFkv1x9-Ebuq1jlF1ggagB4XBxNrgO8eOoXB7oUlcAq92o5NPRcS_21dZgrSepovjopKiIMad6QX7qfbblE7RqFtXUqU93n--aKMsD6xXMw"

  val tokenAExpiration = Instant.parse("2021-11-02T20:32:16Z")

  test("Correct IdentityToken") {
    implicit val clock: Clock[IO] = TestClock.constant(tokenAExpiration.minusSeconds(3))

    implicit val backend: SttpBackend[IO, Any] =
      SttpBackendStub.apply[IO, Any](new CatsMonadAsyncError).whenAnyRequest.thenRespond(trueGoogleCerts)

    TokenValidator.instance[IO](TestClock.syncWithDelegatedClock[IO](clock), backend).validateIdentityToken(tokenA).map { result =>
      assertEquals(result, Some(TargetAudience("http://example.com")))
    }
  }

  test("Expired IdentityToken") {
    implicit val clock: Clock[IO] = TestClock.constant(tokenAExpiration.plusSeconds(3))

    implicit val backend: SttpBackend[IO, Any] =
      SttpBackendStub.apply[IO, Any](new CatsMonadAsyncError).whenAnyRequest.thenRespond(trueGoogleCerts)

    TokenValidator.instance[IO](TestClock.syncWithDelegatedClock[IO](clock), backend).validateIdentityToken(tokenA).map { result =>
      assertEquals(result, None)
    }
  }
}
