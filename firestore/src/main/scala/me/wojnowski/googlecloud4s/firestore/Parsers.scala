package me.wojnowski.googlecloud4s.firestore

import cats.implicits.catsSyntaxEq
import cats.parse.Parser
import cats.parse.Parser0

private[firestore] object Parsers {
  private val MaxLength = 1500

  private val notDots = Parser.not(Parser.start *> Parser.char('.').rep(1, 2) <* Parser.end)

  private val notSlash: Parser[String] = Parser.charWhere(_ =!= '/').rep(1, MaxLength).string

  private val notSurroundedByUnderscores: Parser0[Unit] =
    Parser.not(
      Parser.start *> Parser.string("__") *>
        Parser.charWhere(_ =!= '/').repUntil(Parser.string("__") *> Parser.end).void *>
        Parser.string("__") *> Parser.end
    )

  val idParser: Parser[String] = (notDots *> notSurroundedByUnderscores).with1 *> notSlash
}
