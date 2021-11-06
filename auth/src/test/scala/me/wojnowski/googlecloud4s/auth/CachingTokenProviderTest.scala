package me.wojnowski.googlecloud4s.auth

import cats.effect.Clock
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Sync
import munit.CatsEffectSuite
import cats.syntax.all._

import java.time.Duration
import java.time.Instant

class CachingTokenProviderTest extends CatsEffectSuite {
  val Scopes1 = Scopes("A")

  test("AccessToken cache invalidation") {
    val expiresIn = Duration.ofMinutes(60)

    val buffer = Duration.ofMinutes(30)
    val initialTime = Instant.parse("2000-01-01T00:00:00Z")
    val slightlyBeforeBuffer = Instant.parse("2000-01-01T00:29:59Z")
    val slightlyAfterBuffer = Instant.parse("2000-01-01T00:30:01Z")
    val afterExpirationTime = Instant.parse("2000-01-01T01:00:01Z")

    for {
      ref                       <- Ref[IO].of(initialTime)
      sync = TestClock.syncWithDelegatedClock[IO](TestClock.ref(ref))
      tokenProvider = CachingTokenProviderTest.tokenProvider[IO](sync)
      cachingTokenProvider      <- CachingTokenProvider.instance[IO](tokenProvider, buffer)(sync)
      initialToken              <- cachingTokenProvider.getAccessToken(Scopes1)
      _                         <- ref.set(slightlyBeforeBuffer)
      tokenSlightlyBeforeBuffer <- cachingTokenProvider.getAccessToken(Scopes1)
      _                         <- ref.set(slightlyAfterBuffer)
      tokenSlightlyAfterBuffer  <- cachingTokenProvider.getAccessToken(Scopes1)
      _                         <- ref.set(afterExpirationTime)
      tokenAfterExpirationTime  <- cachingTokenProvider.getAccessToken(Scopes1)
    } yield {
      assertEquals(initialToken.expires, initialTime.plus(expiresIn))
      assertEquals(tokenSlightlyBeforeBuffer.expires, initialTime.plus(expiresIn))
      assertEquals(tokenSlightlyAfterBuffer.expires, slightlyAfterBuffer.plus(expiresIn))
      assertEquals(tokenAfterExpirationTime.expires, afterExpirationTime.plus(expiresIn))
    }
  }
}

object CachingTokenProviderTest {

  def tokenProvider[F[_]: Sync] =
    new TokenProvider[F] {
      override def getAccessToken(scopes: Scopes): F[AccessToken] =
        Clock[F].realTimeInstant.map(instant => AccessToken("A", scopes, instant.plusSeconds(3600)))

      override def getIdentityToken(audience: TargetAudience): F[IdentityToken] =
        Clock[F].realTimeInstant.map(instant => IdentityToken("A", audience, instant.plusSeconds(3600)))
    }

}
