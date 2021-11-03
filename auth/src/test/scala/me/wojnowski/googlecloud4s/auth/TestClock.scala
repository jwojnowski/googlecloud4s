package me.wojnowski.googlecloud4s.auth

import cats.Applicative
import cats.Monad
import cats.effect.Clock
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.kernel.CancelScope
import cats.effect.kernel.Poll

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import cats.syntax.all._

object TestClock {
  def constant[F[_]: Monad](instant: Instant): Clock[F] =
    liftF(instant.pure[F])

  def ref[F[_]: Sync](ref: Ref[F, Instant]): Clock[F] = liftF(ref.get)

  def liftF[F[_]: Monad](f: F[Instant]) =
    new Clock[F] {
      override def applicative: Applicative[F] = Applicative[F]

      override def monotonic: F[FiniteDuration] = ???

      override def realTime: F[FiniteDuration] =
        f.map(instant => FiniteDuration(instant.toEpochMilli, TimeUnit.MILLISECONDS))

    }

  // TODO this shouldn't be necessary in cats-effect 3.3.x (https://github.com/typelevel/cats-effect/issues/1678)
  def syncWithDelegatedClock[F[_]: Sync](clock: Clock[F]): Sync[F] =
    new Sync[F] {
      override def suspend[A](hint: Sync.Type)(thunk: => A): F[A] = Sync[F].suspend(hint)(thunk)

      override def monotonic: F[FiniteDuration] = clock.monotonic

      override def realTime: F[FiniteDuration] = clock.realTime

      override def rootCancelScope: CancelScope = Sync[F].rootCancelScope

      override def forceR[A, B](fa: F[A])(fb: F[B]): F[B] = Sync[F].forceR(fa)(fb)

      override def uncancelable[A](body: Poll[F] => F[A]): F[A] = Sync[F].uncancelable(body)

      override def canceled: F[Unit] = Sync[F].canceled

      override def onCancel[A](fa: F[A], fin: F[Unit]): F[A] = Sync[F].onCancel(fa, fin)

      override def pure[A](x: A): F[A] = Sync[F].pure(x)

      override def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = Sync[F].flatMap(fa)(f)

      override def tailRecM[A, B](a: A)(f: A => F[Either[A, B]]): F[B] = Sync[F].tailRecM(a)(f)

      override def raiseError[A](e: Throwable): F[A] = Sync[F].raiseError(e)

      override def handleErrorWith[A](fa: F[A])(f: Throwable => F[A]): F[A] = Sync[F].handleErrorWith(fa)(f)
    }

}
