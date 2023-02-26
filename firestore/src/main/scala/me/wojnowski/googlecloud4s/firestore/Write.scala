package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.firestore.ProductOps.ProductNameToSnakeCase
import io.circe.syntax._
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields

sealed trait Write extends Product with Serializable {
  def currentDocument: Option[Precondition]
}

object Write {
  final case class Delete(document: Reference.Document, currentDocument: Option[Precondition] = None) extends Write

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

  // TODO move outside of write?
  sealed trait FieldTransform extends Product with Serializable {
    def fieldPath: String // TODO own type
  }

  object FieldTransform {

    // TODO move somewhere else
    private implicit def firestoreCodec[A: FirestoreCodec]: FirestoreCodec[NonEmptyList[A]] =
      new FirestoreCodec[NonEmptyList[A]] {
        override def encode(as: NonEmptyList[A]): Value = Value.Array(as.map(FirestoreCodec[A].encode).toList)

        override def decode(data: Value): Either[FirestoreCodec.Error, NonEmptyList[A]] = ??? // TODO implement
      }

    sealed trait ServerValueFieldTransform extends FieldTransform

    sealed abstract class ValueFieldTransform[A: FirestoreCodec] extends FieldTransform {
      def value: A

      def firestoreValue: Value = FirestoreCodec[A].encode(value)
    }

    final case class SetToServerValue(fieldPath: String, serverValue: ServerValue) extends ServerValueFieldTransform

    // TODO FirestoreData or Long/Double directly?
    // TODO FieldPath
    final case class Increment[A: FirestoreCodec](fieldPath: String, value: A) extends ValueFieldTransform[A]

    final case class Maximum[A: FirestoreCodec](fieldPath: String, value: A) extends ValueFieldTransform[A]

    final case class Minimum[A: FirestoreCodec](fieldPath: String, value: A) extends ValueFieldTransform[A]

    final case class AppendMissingElements[A: FirestoreCodec](fieldPath: String, value: NonEmptyList[A])
      extends ValueFieldTransform[NonEmptyList[A]]

    final case class RemoveAllFromArray[A: FirestoreCodec](fieldPath: String, value: NonEmptyList[A])
      extends ValueFieldTransform[NonEmptyList[A]]

    implicit val encoder: Encoder[FieldTransform] = Encoder.instance { transform =>
      import io.circe.syntax._

      def constructJson(value: Json) =
        JsonObject(
          "fieldPath" -> transform.fieldPath.asJson,
          transform.productPrefixLowerCamelCase -> value
        ).asJson

      transform match {
        case SetToServerValue(_, serverValue)  => constructJson(serverValue.productPrefixUpperSnakeCase.asJson)
        case transform: ValueFieldTransform[_] =>
          constructJson {
            transform.firestoreValue match {
              case Value.Array(values) => JsonObject("values" -> values.map(_.firestoreJson).asJson).asJson // TODO think about it
              case value               => value.firestoreJson.asJson
            }
          }
      }
    }

  }

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
