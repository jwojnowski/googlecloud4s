package me.wojnowski.googlecloud4s.pubsub

import io.circe.Codec
import io.circe.Encoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import me.wojnowski.googlecloud4s.pubsub.Message.Base64Data

import java.util.Base64

// TODO think about separating circe dependency
// TODO think about when should Base64 conversion of data occur
case class Message(
  data: Base64Data,
  attributes: Map[String, String],
  orderingKey: Option[String]
)

object Message {
  type Base64Data = String

  private val base64Encoder = Base64.getEncoder

  implicit val codec: Codec[Message] = deriveCodec

  def create(data: String, attributes: Map[String, String] = Map.empty, orderingKey: Option[String] = None): Message =
    Message(base64Encoder.encodeToString(data.getBytes), attributes, orderingKey)

  def json[A: Encoder](
    data: A,
    attributes: Map[String, String] = Map.empty,
    orderingKey: Option[String] = None
  ): Message = create(data.asJson.noSpaces, attributes, orderingKey)

}
