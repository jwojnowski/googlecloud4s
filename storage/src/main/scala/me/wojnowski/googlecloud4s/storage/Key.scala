package me.wojnowski.googlecloud4s.storage

import cats.Show
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder

case class Key(value: String) extends AnyVal

object Key {

  val codec: Codec[Key] = Codec.from(
    Decoder[String].map(Key.apply),
    Encoder[String].contramap(_.value)
  )

  implicit val show: Show[Key] = Show.show(_.value)
}
