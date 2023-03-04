package me.wojnowski.googlecloud4s.firestore.codec

import cats.data.NonEmptyList
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec.Error

import java.time.Instant
import cats.syntax.all._
import me.wojnowski.googlecloud4s.firestore.Reference
import me.wojnowski.googlecloud4s.firestore.Value

import scala.collection.Factory

trait StandardCodecs {

  implicit val valueCodec: FirestoreCodec[Value] = new FirestoreCodec[Value] {
    override def encode(value: Value): Value = value

    override def decode(value: Value): Either[Error, Value] = Right(value)
  }

  implicit val fieldsCodec: FirestoreCodec[Fields] = new FirestoreCodec[Fields] {
    override def encode(fields: Fields): Value = fields.toMapValue

    override def decode(value: Value): Either[Error, Fields] =
      value.narrowCollect {
        case Value.Map(map) => Fields(map)
      }

  }

  implicit val instantCodec: FirestoreCodec[Instant] = new FirestoreCodec[Instant] {
    override def encode(instant: Instant): Value = Value.Timestamp(instant)

    override def decode(value: Value): Either[Error, Instant] =
      value.narrowCollect[Instant] {
        case Value.Timestamp(instant) => instant
      }

  }

  implicit val booleanCodec: FirestoreCodec[Boolean] = new FirestoreCodec[Boolean] {
    override def encode(boolean: Boolean): Value = Value.Boolean(boolean)

    override def decode(value: Value): Either[Error, Boolean] =
      value.narrowCollect {
        case Value.Boolean(boolean) => boolean
      }

  }

  implicit val bytes: FirestoreCodec[Array[Byte]] = new FirestoreCodec[Array[Byte]] {
    override def encode(bytes: Array[Byte]): Value = Value.Bytes(bytes)

    override def decode(value: Value): Either[Error, Array[Byte]] =
      value.narrowCollect {
        case Value.Bytes(bytes) => bytes
      }

  }

  implicit val intCodec: FirestoreCodec[Int] = new FirestoreCodec[Int] {
    override def encode(int: Int): Value = Value.Integer(int)

    override def decode(value: Value): Either[Error, Int] =
      value.narrowCollect {
        case Value.Integer(int) => int
      }

  }

  implicit val doubleCodec: FirestoreCodec[Double] = new FirestoreCodec[Double] {
    override def encode(double: Double): Value = Value.Double(double)

    override def decode(value: Value): Either[Error, Double] =
      value.narrowCollect {
        case Value.Double(double) => double
      }

  }

  implicit val documentReference: FirestoreCodec[Reference.Document] = new FirestoreCodec[Reference.Document] {
    override def encode(reference: Reference.Document): Value = Value.Reference(reference)

    override def decode(value: Value): Either[Error, Reference.Document] =
      value.narrowCollect {
        case Value.Reference(reference) => reference
      }

  }

  implicit val stringCodec: FirestoreCodec[String] = new FirestoreCodec[String] {
    override def encode(string: String): Value = Value.String(string)

    override def decode(value: Value): Either[Error, String] =
      value.narrowCollect {
        case Value.String(string) => string
      }

  }

  implicit def optionCodec[A: FirestoreCodec]: FirestoreCodec[Option[A]] =
    new FirestoreCodec[Option[A]] {

      override def encode(maybeA: Option[A]): Value =
        maybeA match {
          case Some(value) => FirestoreCodec[A].encode(value)
          case None        => Value.Null
        }

      override def decode(value: Value): Either[Error, Option[A]] =
        value.narrowCollect {
          case Value.Null => none[A].asRight[Error]
          case value      => FirestoreCodec[A].decode(value).map(_.some)
        }.flatten

    }

  implicit def mapCodec[A: FirestoreCodec]: FirestoreCodec[Map[String, A]] =
    new FirestoreCodec[Map[String, A]] {
      override def encode(map: Map[String, A]): Value = Value.Map(map.fmap(FirestoreCodec[A].encode))

      override def decode(value: Value): Either[Error, Map[String, A]] =
        value.narrowCollect {
          case Value.Map(map) =>
            map
              .toList
              .traverse {
                case (key, value) =>
                  FirestoreCodec[A].decode(value).map(key -> _)
              }
              .map(_.toMap)
        }.flatten

    }

  implicit def iterableCodec[A, C[A] <: IterableOnce[A]](
    implicit aCodec: FirestoreCodec[A],
    factory: Factory[A, C[A]]
  ): FirestoreCodec[C[A]] =
    new FirestoreCodec[C[A]] {
      override def encode(collection: C[A]): Value = Value.Array(collection.iterator.map(FirestoreCodec[A].encode).toSeq)

      override def decode(value: Value): Either[Error, C[A]] =
        value.narrowCollect {
          case Value.Array(values) => values.toSeq.traverse(_.as[A]).map(factory.fromSpecific)
        }.flatten

    }

  implicit def nonEmptyListCodec[A: FirestoreCodec]: FirestoreCodec[NonEmptyList[A]] =
    new FirestoreCodec[NonEmptyList[A]] {
      override def encode(as: NonEmptyList[A]): Value = FirestoreCodec[List[A]].encode(as.toList)

      override def decode(value: Value): Either[FirestoreCodec.Error, NonEmptyList[A]] =
        FirestoreCodec[List[A]].decode(value).flatMap {
          case head :: tail => Right(NonEmptyList(head, tail))
          case _            => Left(FirestoreCodec.Error.GenericError("Expected a non-empty array"))
        }

    }

}
