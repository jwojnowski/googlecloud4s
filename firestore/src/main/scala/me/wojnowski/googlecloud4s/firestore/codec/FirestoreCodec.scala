package me.wojnowski.googlecloud4s.firestore.codec

import cats.Invariant
import me.wojnowski.googlecloud4s.firestore.Value

import scala.util.control.NoStackTrace

trait FirestoreCodec[A] {
  def encode(a: A): Value

  def decode(value: Value): Either[FirestoreCodec.Error, A]
}

object FirestoreCodec extends StandardCodecs {

  sealed trait Error extends NoStackTrace with Product with Serializable

  object Error {
    case class UnexpectedValue(encountered: Value) extends Exception(s"Unexpected value type: [${encountered.productPrefix}]") with Error

    case class JsonError(cause: Throwable) extends Exception(cause) with Error

    case class GenericError(message: String) extends Exception(message) with Error
  }

  def apply[A](implicit ev: FirestoreCodec[A]): FirestoreCodec[A] = ev

  def instance[A](encodeFunction: A => Value, decodeFunction: Value => Either[Error, A]): FirestoreCodec[A] =
    new FirestoreCodec[A] {
      override def encode(a: A): Value = encodeFunction(a)

      override def decode(value: Value): Either[Error, A] = decodeFunction(value)
    }

  object syntax {

    implicit class FirestoreCodecOps[A](a: A) {
      def asFirestoreValue(implicit codec: FirestoreCodec[A]): Value = codec.encode(a)
    }

  }

  implicit val invariant: Invariant[FirestoreCodec] = new Invariant[FirestoreCodec] {

    override def imap[A, B](fa: FirestoreCodec[A])(f: A => B)(g: B => A): FirestoreCodec[B] =
      new FirestoreCodec[B] {
        override def encode(b: B): Value =
          fa.encode(g(b))

        override def decode(value: Value): Either[FirestoreCodec.Error, B] =
          fa.decode(value).map(f)
      }

  }

}
