package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Ref
import cats.effect.kernel.Sync

import java.time.Duration
import cats.syntax.all._

object CachingTokenProvider {

  def instance[F[_]: Sync](tokenProvider: TokenProvider[F], expirationBuffer: Duration): F[TokenProvider[F]] =
    for {
      ref <- Ref.of[F, Map[Scope, Token]](Map.empty)
      now <- Clock[F].realTimeInstant
    } yield new TokenProvider[F] {

      override def getToken(scope: Scope): F[Token] =
        ref.get.map(_.get(scope).filter(_.expires.minus(expirationBuffer).isAfter(now))).flatMap {
          case Some(token) => token.pure[F]
          case None        => tokenProvider.getToken(scope).flatTap(token => ref.update(_.updated(scope, token)))
        }

    }

}
