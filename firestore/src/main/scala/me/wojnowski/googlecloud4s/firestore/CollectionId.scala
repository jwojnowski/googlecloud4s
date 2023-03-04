package me.wojnowski.googlecloud4s.firestore

import cats.parse.Parser
import cats.syntax.all._
import io.circe.Decoder
import io.circe.Encoder
import Parsers.idParser

abstract sealed case class CollectionId(value: String)

object CollectionId {
  private[firestore] val parser: Parser[CollectionId] = idParser.map(new CollectionId(_) {})

  def parse(raw: String): Either[String, CollectionId] =
    parser.between(Parser.start, Parser.end).parseAll(raw).leftMap(_.toString)

  def unsafe(raw: String): CollectionId = parse(raw).toOption.getOrElse(throw new ParsingError(s"[$raw] is not a valid collection ID"))

  implicit val encoder: Encoder[CollectionId] = Encoder[String].contramap(_.value)
  implicit val decoder: Decoder[CollectionId] = Decoder[String].emap(CollectionId.parse)

  type ParsingError = IllegalArgumentException
}
