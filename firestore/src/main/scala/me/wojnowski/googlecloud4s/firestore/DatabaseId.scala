package me.wojnowski.googlecloud4s.firestore

import cats.parse.Parser
import cats.implicits._

abstract sealed case class DatabaseId(value: String)

object DatabaseId {
  val default: DatabaseId = new DatabaseId("(default)") {}

  def parse(raw: String): Either[String, DatabaseId] =
    Parsing.parser.between(Parser.start, Parser.end).parseAll(raw).leftMap(_.toString)

  def unsafe(raw: String): DatabaseId = parse(raw).toOption.getOrElse(throw new Parsing.Error(s"[$raw] is not a valid collection ID"))

  private[firestore] object Parsing {
    private val lowercase = Parser.charIn('a' to 'z')
    private val digit = Parser.charIn('0' to '9')
    private val dash = Parser.charIn('-')
    private val allowedChars = lowercase | digit | dash
    private val defaultParser = Parser.string(default.value).string

    private def uuidPart(length: Int) =
      (digit | Parser.charIn('a' to 'f')).rep(length, length)

    private val uuidParser =
      uuidPart(8) ~ dash ~
        uuidPart(4) ~ dash ~
        uuidPart(4) ~ dash ~
        uuidPart(4) ~ dash ~
        uuidPart(12)

    private[firestore] val parser: Parser[DatabaseId] =
      (Parser.not(uuidParser).with1 *> (
        defaultParser |
          allowedChars
            .repUntil((lowercase | digit) *> Parser.not(allowedChars))
            .filter(chars => (2 to 61).contains(chars.length))
            .between(lowercase, lowercase | digit)
            .string
      )).map(new DatabaseId(_) {})

    type Error = IllegalArgumentException

  }

}
