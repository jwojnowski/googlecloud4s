package me.wojnowski.googlecloud4s

import cats.Eq

case class ProjectId(value: String)

object ProjectId {
  implicit val eq: Eq[ProjectId] = Eq.by(_.value)
}
