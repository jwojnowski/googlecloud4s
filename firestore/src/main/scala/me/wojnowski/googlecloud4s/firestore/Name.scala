package me.wojnowski.googlecloud4s.firestore

import cats.Show
import cats.syntax.all._
import cats.parse.Parser

sealed abstract case class Name(value: String) {}

object Name {

  private[firestore] val parser = Parser.charsWhile(_ =!= '/').map(new Name(_) {})

  def parse(raw: String): Either[ParsingError, Name] =
    parser
      .between(Parser.start, Parser.end)
      .parseAll(raw)
      .leftMap(error => new ParsingError(error.toString))

  type ParsingError = IllegalArgumentException

  implicit val show: Show[Name] = Show.fromToString
}
