package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Encoder

case class DocumentMask(fieldPaths: NonEmptyList[FieldPath])

object DocumentMask {
  def apply(head: FieldPath, tail: FieldPath*): DocumentMask = DocumentMask(NonEmptyList(head, tail.toList))

  implicit val encoder: Encoder[DocumentMask] = Encoder.forProduct1[DocumentMask, NonEmptyList[FieldPath]]("fieldPaths")(_.fieldPaths)
}
