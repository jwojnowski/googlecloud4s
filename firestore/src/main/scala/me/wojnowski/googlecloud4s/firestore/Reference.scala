package me.wojnowski.googlecloud4s.firestore

import cats.Eq
import cats.Show
import cats.parse.Parser
import cats.parse.Parser0
import me.wojnowski.googlecloud4s.ProjectId
import cats.syntax.all._
import io.circe.Decoder

sealed trait Reference extends Product with Serializable {
  def resolve(collectionId: CollectionId, documentName: DocumentId): Reference.Document =
    Reference.Document(this, collectionId, documentName)

  def full: String

  def contains(other: Reference): Boolean
}

object Reference {

  case class Root(projectId: ProjectId) extends Reference {
    def full = s"projects/${projectId.value}/databases/${Firestore.defaultDatabase}/documents"

    override def contains(other: Reference): Boolean =
      other =!= this

    override def toString: String = full
  }

  object Root {
    implicit val eq: Eq[Reference.Root] = Eq.fromUniversalEquals
  }

  case class Document(parent: Reference, collectionId: CollectionId, documentId: DocumentId) extends Reference {
    def full = s"${parent.full}/${collectionId.value}/${documentId.value}"

    override def toString: String = full

    override def contains(other: Reference): Boolean =
      (other == this) || parent.contains(other)
  }

  object Document {

    def parse(raw: String): Either[ParsingError, Reference.Document] =
      Reference.parse(raw).flatMap {
        case reference: Reference.Document => Right(reference)
        case _                             => Left(new ParsingError("expected non-root path"))
      }

    implicit val fullyQualifiedNameDecoder: Decoder[Reference.Document] =
      Decoder[String].emap(Reference.Document.parse(_).leftMap(_.getMessage))
    implicit val ordering: Ordering[Reference.Document] = Ordering.by(_.full)

    implicit val show: Show[Reference.Document] = Show.show(_.full)

    implicit val eq: Eq[Reference.Document] = Eq.fromUniversalEquals

  }

  implicit val eq: Eq[Reference] = Eq.instance {
    case (a: Reference.Document, b: Reference.Document) => a === b
    case (a: Reference.Root, b: Reference.Root)         => a === b
    case _                                              => false
  }

  private val parser: Parser0[Reference] =
    for {
      _                   <- Parser.start
      _                   <- Parser.string("projects")
      _                   <- Parser.char('/')
      projectId           <- Parser.charsWhile(_ =!= '/')
      _                   <- Parser.char('/')
      _                   <- Parser.string("databases")
      _                   <- Parser.char('/')
      _                   <- Parser.string(Firestore.defaultDatabase)
      _                   <- Parser.char('/')
      _                   <- Parser.string("documents")
      collectionNamePairs <- (CollectionId.parser.surroundedBy(Parser.char('/')) ~ DocumentId.parser).rep0
      _                   <- Parser.end
    } yield collectionNamePairs.foldLeft[Reference](Root(ProjectId(projectId))) {
      case (reference, (collectionId, documentName)) => Document(reference, collectionId, documentName)
    }

  def parse(raw: String): Either[ParsingError, Reference] = parser.parseAll(raw).leftMap(error => new ParsingError(error.toString))

  type ParsingError = IllegalArgumentException
}
