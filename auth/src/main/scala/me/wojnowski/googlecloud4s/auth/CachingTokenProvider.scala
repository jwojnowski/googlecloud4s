package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.Ref
import cats.effect.kernel.Sync

import java.time.Duration
import cats.syntax.all._

object CachingTokenProvider {

  def instance[F[_]: Sync](tokenProvider: TokenProvider[F], expirationBuffer: Duration): F[TokenProvider[F]] =
    for {
      ref <- Ref.of[F, (Map[Scopes, AccessToken], Map[TargetAudience, IdentityToken])]((Map.empty, Map.empty))
      now <- Clock[F].realTimeInstant
    } yield new TokenProvider[F] {

      override def getAccessToken(scopes: Scopes): F[AccessToken] =
        ref.get.map(_._1.get(scopes).filter(_.expires.minus(expirationBuffer).isAfter(now))).flatMap {
          case Some(token) => token.pure[F]
          case None        =>
            tokenProvider
              .getAccessToken(scopes)
              .flatTap(token =>
                ref.update {
                  case (accessTokens, identityTokens) =>
                    (accessTokens.updated(scopes, token), identityTokens)
                }
              )
        }

      override def getIdentityToken(audience: TargetAudience): F[IdentityToken] =
        ref.get.map(_._2.get(audience).filter(_.expires.minus(expirationBuffer).isAfter(now))).flatMap {
          case Some(token) => token.pure[F]
          case None        =>
            tokenProvider
              .getIdentityToken(audience)
              .flatTap(token =>
                ref.update {
                  case (accessTokens, identityTokens) =>
                    (accessTokens, identityTokens.updated(audience, token))
                }
              )
        }

    }

}
