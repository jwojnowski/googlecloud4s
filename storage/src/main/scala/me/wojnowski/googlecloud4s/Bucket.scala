package me.wojnowski.googlecloud4s

import cats.Eq
import cats.Show
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

case class Bucket(value: String) extends AnyVal

object Bucket {
  implicit val eq: Eq[Bucket] = Eq.by[Bucket, String](_.value)
  implicit val codec: Codec[Bucket] = deriveUnwrappedCodec
  implicit val show: Show[Bucket] = Show.show(_.value)
}