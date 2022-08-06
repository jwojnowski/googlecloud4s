package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import me.wojnowski.googlecloud4s.auth.AccessToken
import me.wojnowski.googlecloud4s.auth.IdentityToken
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.auth.TargetAudience
import me.wojnowski.googlecloud4s.auth.TokenProvider

import java.time.Instant

object TokenProviderMock {

  val instance = new TokenProvider[IO] {

    override def getAccessToken(scopes: Scopes): IO[AccessToken] =
      IO.pure(
        AccessToken(
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJsb2dnZWRJbkFzIjoiYWRtaW4iLCJpYXQiOjE0MjI3Nzk2Mzh9.gzSraSYS8EXBxLN_oWnFSRgCzcmJmMjLiuyu5CSpyHI",
          Scopes("test"),
          Instant.EPOCH
        )
      )

    override def getIdentityToken(audience: TargetAudience): IO[IdentityToken] = ???
  }

}
