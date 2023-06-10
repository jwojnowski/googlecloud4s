package me.wojnowski.googlecloud4s.firestore.codec

import io.circe.Json
import cats.syntax.all._
import FirestoreCodec.Error
import me.wojnowski.googlecloud4s.firestore.Value

trait CirceCodecs {

  import io.circe.syntax._
  import io.circe.Decoder
  import io.circe.Encoder

  implicit def circeFirestoreCodec[A: Encoder: Decoder]: FirestoreCodec[A] =
    new FirestoreCodec[A] {

      override def encode(a: A): Value = jsonToValue(a.asJson)

      private def jsonToValue(json: Json): Value =
        json.fold(
          Value.Null,
          Value.Boolean.apply,
          number => number.toLong.map(Value.Integer.apply).getOrElse(Value.Double.apply(number.toDouble)),
          Value.String.apply,
          array => Value.Array(array.map(jsonToValue)),
          jsonObject => Value.Map(jsonObject.toMap.fmap(jsonToValue))
        )

      override def decode(value: Value): Either[Error, A] =
        value.plainJson.as[A].leftMap(Error.JsonError.apply)

    }

}
