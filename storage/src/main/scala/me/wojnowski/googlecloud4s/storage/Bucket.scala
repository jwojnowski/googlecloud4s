package me.wojnowski.googlecloud4s.storage

import cats.Eq
import cats.Show
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder

case class Bucket(value: String) extends AnyVal

object Bucket {
  implicit val eq: Eq[Bucket] = Eq.by[Bucket, String](_.value)

  val codec: Codec[Bucket] = Codec.from(
    Decoder[String].map(Bucket.apply),
    Encoder[String].contramap(_.value)
  )

  implicit val show: Show[Bucket] = Show.show(_.value)
}
