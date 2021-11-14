package me.wojnowski.googlecloud4s.pubsub

import io.circe.Decoder

case class PushMessageEnvelope(message: IncomingMessage, subscription: String)

object PushMessageEnvelope {
  implicit val decoder: Decoder[PushMessageEnvelope] =
    Decoder.forProduct2[PushMessageEnvelope, IncomingMessage, String]("message", "subscription")(PushMessageEnvelope.apply)
}
