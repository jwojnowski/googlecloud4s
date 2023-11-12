package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.syntax._
import io.circe.Encoder
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields

sealed trait Write extends Product with Serializable {
  def documentReference: Reference.Document
  def currentDocument: Option[Precondition]
}

object Write {
  final case class Delete(documentReference: Reference.Document, currentDocument: Option[Precondition] = None) extends Write

  final case class Update(
    documentReference: Reference.Document,
    fields: Fields,
    updateTransforms: List[FieldTransform] = List.empty,
    updateMask: Option[DocumentMask] = None,
    currentDocument: Option[Precondition] = None
  ) extends Write

  final case class DocumentTransform(
    documentReference: Reference.Document,
    fieldTransforms: NonEmptyList[FieldTransform],
    currentDocument: Option[Precondition] = None
  ) extends Write

  implicit val encoder: Encoder.AsObject[Write] = Encoder.AsObject.instance {
    case Delete(reference, currentDocument)                                      =>
      JsonObject.fromMap(Map("delete" -> reference.asJson) ++ currentDocument.map("precondition" -> _.asJson))
    case Update(reference, value, updateTransforms, updateMask, currentDocument) =>
      JsonObject
        .fromMap(
          Map("update" -> JsonObject("name" -> reference.asJson, "fields" -> value.asJson).asJson) ++
            updateMask.map("updateMask" -> _.asJson) ++
            currentDocument.map("currentDocument" -> _.asJson) ++
            Option.when(updateTransforms.nonEmpty)(updateTransforms).map("updateTransforms" -> _.asJson)
        )
    case DocumentTransform(documentReference, fieldTransforms, currentDocument)  =>
      JsonObject
        .fromMap(
          Map(
            "transform" -> JsonObject(
              "document" -> documentReference.asJson,
              "fieldTransforms" -> fieldTransforms.asJson
            ).asJson
          ) ++ currentDocument.map("currentDocument" -> _.asJson)
        )
  }

}
