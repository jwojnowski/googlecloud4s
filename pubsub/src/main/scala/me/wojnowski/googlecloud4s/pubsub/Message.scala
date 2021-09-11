package me.wojnowski.googlecloud4s.pubsub

import cats.effect.Clock
import cats.effect.Sync
import io.chrisdavenport.fuuid.FUUID
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.OffsetDateTime
import java.time.ZoneOffset
import cats.syntax.all._
import fs2.Chunk
import io.chrisdavenport.fuuid.FUUIDGen
import io.chrisdavenport.fuuid.circe._
import io.circe.syntax._
import fs2.Stream
import me.wojnowski.googlecloud4s.pubsub.Message.Base64Data

// TODO think about separating circe dependency
case class Message(
  data: Base64Data,
  messageId: FUUID,
  publishTime: OffsetDateTime,
  attributes: Map[String, String] = Map.empty,
  orderingKey: Option[String] = None
)

object Message {
  type Base64Data = String

  implicit val encoder: Encoder[Message] = deriveEncoder

  def create[F[_]: Sync: FUUIDGen: Clock](
    data: Array[Byte],
    attributes: Map[String, String] = Map.empty,
    orderingKey: Option[String] = None,
    zoneOffset: ZoneOffset = ZoneOffset.UTC
  ): F[Message] =
    for {
      instant    <- Clock[F].realTimeInstant
      fuuid      <- FUUIDGen[F].random
      dataBase64 <- Stream.chunk(Chunk.array(data)).covary[F].through(fs2.text.base64.encode).compile.string
    } yield Message(dataBase64, fuuid, instant.atOffset(zoneOffset), attributes, orderingKey)

  def json[F[_]: Sync: FUUIDGen: Clock, A: Encoder](
    data: A,
    attributes: Map[String, String] = Map.empty,
    orderingKey: Option[String] = None,
    zoneOffset: ZoneOffset = ZoneOffset.UTC
  ): F[Message] = create(data.asJson.noSpaces.getBytes, attributes, orderingKey, zoneOffset)

}
