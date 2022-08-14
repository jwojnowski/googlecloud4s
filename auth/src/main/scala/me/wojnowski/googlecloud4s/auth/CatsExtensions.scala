package me.wojnowski.googlecloud4s.auth

import cats.MonadError

object CatsExtensions {

  implicit class FEitherExtensions[F[_], E, B](fea: F[Either[E, B]]) {

    def rethrowSome[E1 <: E](pf: PartialFunction[E, E1])(implicit F: MonadError[F, _ >: E]): F[Either[E1, B]] =
      F.flatMap(fea) {
        case Right(a)                     => F.pure(Right(a))
        case Left(a) if pf.isDefinedAt(a) => F.pure(Left(pf.apply(a)))
        case Left(a)                      => F.raiseError[Either[E1, B]](a)
      }

  }

}
