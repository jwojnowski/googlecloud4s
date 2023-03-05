package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Decoder
import me.wojnowski.googlecloud4s.firestore.BatchWriteResponse.Status

case class BatchWriteResponse(writeResults: NonEmptyList[WriteResult], status: NonEmptyList[Status])

object BatchWriteResponse {
  // TODO details?
  case class Status(code: Option[Int], message: Option[String])

  object Status {
    implicit val decoder: Decoder[Status] = Decoder.forProduct2[Status, Option[Int], Option[String]]("code", "message")(Status.apply)
  }

  implicit val decoder: Decoder[BatchWriteResponse] =
    Decoder.forProduct2[BatchWriteResponse, NonEmptyList[WriteResult], NonEmptyList[Status]]("writeResults", "status")(
      BatchWriteResponse.apply
    )

}
