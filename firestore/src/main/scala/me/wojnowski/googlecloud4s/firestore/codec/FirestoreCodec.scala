package me.wojnowski.googlecloud4s.firestore.codec

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

  object syntax {

    implicit class FirestoreCodecOps[A](a: A) {
      def asFirestoreValue(implicit codec: FirestoreCodec[A]): Value = codec.encode(a)
    }

  }

}
