package me.wojnowski.googlecloud4s

import scala.util.control.NoStackTrace

private[googlecloud4s] trait ProductSerializableNoStacktrace extends NoStackTrace with Product with Serializable {
  override def toString: String = productIterator.mkString(productPrefix + "(", ",", ")")
}
