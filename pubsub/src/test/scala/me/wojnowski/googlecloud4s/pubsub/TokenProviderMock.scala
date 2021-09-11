package me.wojnowski.googlecloud4s.pubsub

import cats.Applicative
import me.wojnowski.googlecloud4s.auth.Scope
import me.wojnowski.googlecloud4s.auth.Token
import me.wojnowski.googlecloud4s.auth.TokenProvider
import cats.syntax.all._

object TokenProviderMock {

  def const[F[_]: Applicative](token: Token): TokenProvider[F] =
    new TokenProvider[F] {
      override def getToken(scope: Scope): F[Token] = token.pure[F]
    }

}
