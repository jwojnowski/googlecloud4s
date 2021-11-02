package me.wojnowski.googlecloud4s.pubsub

import cats.Applicative
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.auth.TokenProvider
import cats.syntax.all._
import me.wojnowski.googlecloud4s.auth.AccessToken
import me.wojnowski.googlecloud4s.auth.IdentityToken
import me.wojnowski.googlecloud4s.auth.TargetAudience

object TokenProviderMock {

  def const[F[_]: Applicative](accessToken: AccessToken): TokenProvider[F] =
    new TokenProvider[F] {
      override def getAccessToken(scopes: Scopes): F[AccessToken] = accessToken.pure[F]

      override def getIdentityToken(audience: TargetAudience): F[IdentityToken] = ???
    }

}
