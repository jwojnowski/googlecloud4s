package me.wojnowski.googlecloud4s.firestore

import io.circe.syntax._
import io.circe.Encoder
import io.circe.JsonObject

import java.time.Instant

sealed trait Precondition extends Product with Serializable

object Precondition {
  case class Exists(exists: Boolean) extends Precondition

  case class UpdateTime(updateTime: Instant) extends Precondition

  implicit val encoder: Encoder[Precondition] = Encoder.instance {
    case Exists(exists)         => JsonObject("exists" -> exists.asJson).asJson
    case UpdateTime(updateTime) => JsonObject("updateTime" -> updateTime.asJson).asJson
  }

}
