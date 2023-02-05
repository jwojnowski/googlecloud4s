package me.wojnowski.googlecloud4s.firestore

import cats.Show
import cats.syntax.all._
import cats.parse.Parser
import Parsers._

sealed abstract case class DocumentId(value: String) {}

object DocumentId {

  private[firestore] val parser = idParser.map(new DocumentId(_) {})

  def parse(raw: String): Either[ParsingError, DocumentId] =
    parser
      .between(Parser.start, Parser.end)
      .parseAll(raw)
      .leftMap(error => new ParsingError(error.toString))

  type ParsingError = IllegalArgumentException

  implicit val show: Show[DocumentId] = Show.fromToString
}
