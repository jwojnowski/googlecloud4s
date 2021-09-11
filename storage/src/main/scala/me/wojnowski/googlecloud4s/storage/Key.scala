package me.wojnowski.googlecloud4s.storage

import cats.Show
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

case class Key(value: String) extends AnyVal

object Key {
  implicit val codec: Codec[Key] = deriveUnwrappedCodec
  implicit val show: Show[Key] = Show.show(_.value)
}
