package me.wojnowski.googlecloud4s.pubsub

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class PushMessageEnvelope(message: IncomingMessage, subscription: String)

object PushMessageEnvelope {
  implicit val decoder: Decoder[PushMessageEnvelope] = deriveDecoder
}
