package me.wojnowski.googlecloud4s.firestore

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.firestore
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec

import java.time.Instant
import java.util.Base64
import scala.util.Try
import cats.syntax.all._
import io.circe.syntax.EncoderOps

sealed abstract class Value(val jsonKey: String) extends Product with Serializable {
  def as[A](implicit codec: FirestoreCodec[A]): Either[FirestoreCodec.Error, A] = codec.decode(this)

  def narrowCollect[A](partialFunction: PartialFunction[Value, A]): Either[FirestoreCodec.Error, A] =
    partialFunction
      .unapply(this)
      .toRight(FirestoreCodec.Error.UnexpectedValue(this))

  def asMap: Option[Value.Map] =
    this match {
      case map: Value.Map => Some(map)
      case _              => None
    }

  def asArray: Option[Value.Array] =
    this match {
      case array: Value.Array => Some(array)
      case _                  => None
    }

}

object Value {

  case object Null extends Value("nullValue")
  case class Boolean(value: scala.Boolean) extends Value("booleanValue")
  case class Integer(value: Int) extends Value("integerValue")
  case class Double(value: scala.Double) extends Value("doubleValue")
  case class Timestamp(value: Instant) extends Value("timestampValue")
  case class String(value: java.lang.String) extends Value("stringValue")
  case class Bytes(value: scala.Array[Byte]) extends Value("bytesValue")
  case class Reference(value: firestore.Reference.Document) extends Value("referenceValue")
  case class GeoPoint(latitude: scala.Double, longitude: scala.Double) extends Value("geoPointValue")
  case class Array(value: Iterable[Value]) extends Value("arrayValue")

  object Array {
    def apply(): Array = Array(List.empty)

    def apply[V <: Value](head: V, tail: V*): Array = Array(head +: tail)
  }

  case class Map(value: scala.collection.immutable.Map[java.lang.String, Value]) extends Value("mapValue") {
    def apply(fieldName: java.lang.String): Option[Value] = value.get(fieldName)
  }

  object Map {
    def apply(fields: (java.lang.String, Value)*): Map = Map(scala.collection.immutable.Map.from(fields))
  }

  def fromFirestoreJson(json: Json): Either[java.lang.String, Value] =
    json.asObject.toRight("Expected JSON object").flatMap { jsonObject =>
      jsonObject.toList match {
        case List(("nullValue", Json.Null))  => Right(Value.Null)
        case List(("booleanValue", value))   => value.as[scala.Boolean].leftMap(_.getMessage).map(Boolean.apply)
        case List(("integerValue", value))   => value.as[Int].leftMap(_.getMessage).map(Integer.apply)
        case List(("doubleValue", value))    => value.as[scala.Double].leftMap(_.getMessage).map(Double.apply)
        case List(("timestampValue", value)) => value.as[Instant].leftMap(_.getMessage).map(Timestamp.apply)
        case List(("stringValue", value))    => value.as[java.lang.String].leftMap(_.getMessage).map(String.apply)
        case List(("bytesValue", value))     =>
          value
            .as[java.lang.String]
            .flatMap(string => Try(Base64.getUrlDecoder.decode(string)).toEither)
            .leftMap(_.getMessage)
            .map(Bytes.apply)
        case List(("referenceValue", value)) =>
          value.as[firestore.Reference.Document].leftMap(_.getMessage).map(Reference.apply)
        case List(("geoPointValue", value))  =>
          (
            value.hcursor.downField("latitude").as[scala.Double],
            value.hcursor.downField("longitude").as[scala.Double]
          ).mapN((latitude, longitude) => GeoPoint(latitude, longitude)).leftMap(_.getMessage)
        case List(("arrayValue", value))     =>
          value.hcursor.downField("values").as[List[Json]].leftMap(_.getMessage).flatMap(_.traverse(fromFirestoreJson)).map(Array.apply)
        case List(("mapValue", value))       =>
          value.hcursor.downField("fields").as[scala.collection.immutable.Map[java.lang.String, Json]].leftMap(_.getMessage).flatMap {
            _.toList
              .traverse { case (fieldName, fieldValue) => fromFirestoreJson(fieldValue).map(fieldName -> _) }
              .map(fields => Map(fields.toMap))
          }
        case _                               =>
          Left("Could not decode value as any of known types")
      }
    }

  implicit class FirestoreJsonValue(value: Value) {

    def plainJson: Json =
      value match {
        case Null                          => Json.Null
        case Boolean(value)                => value.asJson
        case Integer(value)                => value.asJson
        case Double(value)                 => value.asJson
        case Timestamp(value)              => value.asJson
        case String(value)                 => value.asJson
        case Bytes(value)                  => new java.lang.String(Base64.getUrlEncoder.encode(value)).asJson
        case Reference(value)              => value.full.asJson
        case GeoPoint(latitude, longitude) =>
          JsonObject(
            "latitude" -> latitude.asJson,
            "longitude" -> longitude.asJson
          ).asJson
        case Array(value)                  => value.map(_.plainJson).asJson
        case Map(value)                    => value.fmap(_.plainJson.asJson).asJson
      }

    def firestoreJson: JsonObject =
      value match {
        case Array(values) => JsonObject("arrayValue" -> JsonObject("values" -> values.map(_.firestoreJson).asJson).asJson)
        case Map(values)   => JsonObject("mapValue" -> JsonObject("fields" -> values.fmap(_.firestoreJson.asJson).asJson).asJson)
        case value         => JsonObject(value.jsonKey -> value.plainJson)
      }

  }

  implicit val decoder: Decoder[Value] =
    Decoder.instance(hCursor => Value.fromFirestoreJson(hCursor.value).leftMap(DecodingFailure.apply(_, List.empty)))

}
