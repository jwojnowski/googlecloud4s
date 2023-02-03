package me.wojnowski.googlecloud4s.firestore

import cats.parse.Parser
import cats.syntax.all._
import io.circe.Decoder
import io.circe.Encoder

abstract sealed case class CollectionId(name: String)

object CollectionId {
  private[firestore] val parser: Parser[CollectionId] = Parser.charsWhile(_ =!= '/').map(new CollectionId(_) {})

  def parse(rawId: String): Either[String, CollectionId] =
    parser.between(Parser.start, Parser.end).parseAll(rawId).leftMap(_.toString)

  implicit val encoder: Encoder[CollectionId] = Encoder[String].contramap(_.name)
  implicit val decoder: Decoder[CollectionId] = Decoder[String].emap(CollectionId.parse)

  type ParsingError = IllegalArgumentException
}
