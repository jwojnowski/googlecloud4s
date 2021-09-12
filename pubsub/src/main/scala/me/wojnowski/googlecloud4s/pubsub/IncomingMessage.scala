package me.wojnowski.googlecloud4s.pubsub

import io.circe.Decoder

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.Base64
import scala.util.Try

case class IncomingMessage(data: String, attributes: Map[String, String], messageId: String, publishTime: OffsetDateTime)

object IncomingMessage {

  private val base64Decoder = Base64.getDecoder

  implicit val decoder: Decoder[IncomingMessage] = Decoder
    .forProduct4[(String, Option[Map[String, String]], String, OffsetDateTime), String, Option[
      Map[String, String]
    ], String, OffsetDateTime]("data", "attributes", "messageId", "publishTime")(Tuple4.apply)
    .emapTry {
      case (base64Data, maybeAttributes, messageId, publishTime) =>
        Try(new String(base64Decoder.decode(base64Data), StandardCharsets.UTF_8)).map { decodedData =>
          IncomingMessage(decodedData, maybeAttributes.getOrElse(Map.empty), messageId, publishTime)
        }
    }

  object circe {

    import io.circe.parser.decode

    implicit class JsonIncomingMessage(message: IncomingMessage) {
      def dataAs[A: Decoder]: Either[io.circe.Error, A] = decode[A](message.data)
    }

  }

}
