package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Decoder
import me.wojnowski.googlecloud4s.firestore.Value

import java.time.Instant

case class WriteResult(updateTime: Option[Instant], transformResults: Option[NonEmptyList[Value]])

object WriteResult {
  implicit val decoder: Decoder[WriteResult] =
    Decoder.forProduct2[WriteResult, Option[Instant], Option[NonEmptyList[Value]]]("updateTime", "transformResults")(WriteResult.apply)
}
