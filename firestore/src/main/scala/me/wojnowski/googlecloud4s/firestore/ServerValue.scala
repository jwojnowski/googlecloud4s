package me.wojnowski.googlecloud4s.firestore

sealed trait ServerValue extends Product with Serializable

object ServerValue {
  case object Unspecified extends ServerValue

  case object RequestTime extends ServerValue
}
