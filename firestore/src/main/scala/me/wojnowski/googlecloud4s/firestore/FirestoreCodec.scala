package me.wojnowski.googlecloud4s.firestore

import cats.implicits._
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.JsonObject

trait FirestoreCodec[A] {
  def encode(a: A): FirestoreData

  def decode(data: FirestoreData): Either[FirestoreCodec.Error, A]
}

object FirestoreCodec {
  case class Error(cause: Throwable) extends Exception(cause) with Product with Serializable

  def apply[A](implicit ev: FirestoreCodec[A]): FirestoreCodec[A] = ev

  object syntax {

    implicit class FirestoreCodecOps[A](a: A) {
      def asFirestoreData(implicit codec: FirestoreCodec[A]): FirestoreData = codec.encode(a)
    }

  }

  object circe {
    import io.circe.Decoder
    import io.circe.Encoder
    import io.circe.syntax._

    implicit def circeFirestoreCodec[A: Encoder: Decoder]: FirestoreCodec[A] =
      new FirestoreCodec[A] {

        override def encode(a: A): FirestoreData = {
          def convert(sourceJson: Json): JsonObject =
            sourceJson.fold(
              JsonObject("nullValue" -> Json.Null),
              boolean => JsonObject("booleanValue" -> boolean.asJson),
              number => JsonObject("doubleValue" -> number.asJson),
              string => JsonObject("stringValue" -> string.asJson),
              array => JsonObject("arrayValue" -> JsonObject("values" -> array.map(convert(_).asJson).asJson).asJson),
              obj => JsonObject("mapValue" -> JsonObject("fields" -> obj.mapValues(convert(_).asJson).asJson).asJson)
            )

          FirestoreData(
            convert(a.asJson)
          )
        }

        override def decode(data: FirestoreData): Either[Error, A] = {
          def convert(sourceJson: JsonObject): Decoder.Result[Json] =
            sourceJson.toList.headOption.toRight(DecodingFailure("Empty JsonObject", List.empty)).flatMap { // TODO parsing failure
              case ("mapValue", value)   =>
                value.as[JsonObject].flatMap {
                  _.apply("fields").toRight(DecodingFailure("No 'fields' field", List.empty)).flatMap(_.as[JsonObject]).flatMap { obj =>
                    obj
                      .toList
                      .traverse[Decoder.Result, (String, Json)] { case (key, value) => value.as[JsonObject].flatMap(convert).map(key -> _) }
                      .map(JsonObject.apply)
                      .map(_.asJson)
                  }
                }
              case ("arrayValue", value) =>
                value.as[JsonObject].flatMap { jsonObject =>
                  jsonObject.apply("values").getOrElse(Json.arr()).as[List[JsonObject]].flatMap { objs =>
                    objs.traverse(convert).map(_.asJson)
                  }
                }
              case (_, value)            =>
                Right(value)
            }

          convert(data.json).flatMap(_.asJson.as[A]).leftMap(Error)
        }

      }

  }

}

// TODO json: JsonObject?
case class FirestoreData(json: JsonObject) extends AnyVal {
  def as[A](implicit codec: FirestoreCodec[A]): Either[FirestoreCodec.Error, A] = codec.decode(this)
}
