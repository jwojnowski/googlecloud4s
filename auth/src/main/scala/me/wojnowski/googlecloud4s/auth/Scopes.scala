package me.wojnowski.googlecloud4s.auth

import cats.data.NonEmptyList

case class Scopes(values: NonEmptyList[String])

object Scopes {
  def apply(value: String): Scopes = Scopes(NonEmptyList.one(value))
}
