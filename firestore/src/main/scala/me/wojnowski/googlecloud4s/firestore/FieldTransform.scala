package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.firestore.ProductOps.ProductNameToSnakeCase
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec

sealed trait FieldTransform extends Product with Serializable {
  def fieldPath: FieldPath
}

object FieldTransform {

  sealed trait ServerValueFieldTransform extends FieldTransform

  sealed abstract class ValueFieldTransform[A: FirestoreCodec] extends FieldTransform {
    def value: A

    def firestoreValue: Value = FirestoreCodec[A].encode(value)
  }

  final case class SetToServerValue(fieldPath: FieldPath, serverValue: ServerValue) extends ServerValueFieldTransform

  final case class Increment[A: FirestoreCodec](fieldPath: FieldPath, value: A) extends ValueFieldTransform[A]

  final case class Maximum[A: FirestoreCodec](fieldPath: FieldPath, value: A) extends ValueFieldTransform[A]

  final case class Minimum[A: FirestoreCodec](fieldPath: FieldPath, value: A) extends ValueFieldTransform[A]

  final case class AppendMissingElements[A: FirestoreCodec](fieldPath: FieldPath, value: NonEmptyList[A])
    extends ValueFieldTransform[NonEmptyList[A]]

  final case class RemoveAllFromArray[A: FirestoreCodec](fieldPath: FieldPath, value: NonEmptyList[A])
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
            case Value.Array(values) =>
              JsonObject("values" -> values.map(_.firestoreJson).asJson).asJson // TODO refactor when taking care of JSON encoding
            case value               =>
              value.firestoreJson.asJson
          }
        }
    }
  }

}
