package me.wojnowski.googlecloud4s.firestore

import io.circe.Decoder
import io.circe.Encoder

case class TestDocumentWithCounter(counter: Int)

object TestDocumentWithCounter {
  implicit val encoder: Encoder[TestDocumentWithCounter] = Encoder.forProduct1("counter")(_.counter)
  implicit val decoder: Decoder[TestDocumentWithCounter] = Decoder.forProduct1("counter")(TestDocumentWithCounter.apply)
}
