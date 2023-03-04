package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Encoder

case class FieldPath(segments: NonEmptyList[String]) {
  def dotted: String = segments.toList.mkString(".")
}

object FieldPath {
  def apply(head: String, tail: String*): FieldPath = FieldPath(NonEmptyList(head, tail.toList))

  def unsafe(s: String): FieldPath = FieldPath(NonEmptyList.fromListUnsafe(s.split('.').toList))

  implicit val encoder: Encoder[FieldPath] = Encoder[String].contramap(_.dotted)
}
