package me.wojnowski.googlecloud4s.pubsub

import io.circe.Encoder
import io.circe.syntax._

import java.util.Base64

// TODO think about separating circe dependency
case class OutgoingMessage(
  data: String,
  attributes: Map[String, String] = Map.empty,
  orderingKey: Option[String] = None
)

object OutgoingMessage {
  private val base64Encoder = Base64.getEncoder

  implicit val encoder: Encoder[OutgoingMessage] =
    Encoder.forProduct3[OutgoingMessage, String, Map[String, String], Option[String]]("data", "attributes", "orderingKey") { message =>
      (base64Encoder.encodeToString(message.data.getBytes), message.attributes, message.orderingKey)
    }

  def json[A: Encoder](
    data: A,
    attributes: Map[String, String] = Map.empty,
    orderingKey: Option[String] = None
  ): OutgoingMessage = OutgoingMessage(data.asJson.noSpaces, attributes, orderingKey)

}
