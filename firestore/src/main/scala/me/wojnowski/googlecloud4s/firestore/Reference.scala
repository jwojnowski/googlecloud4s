package me.wojnowski.googlecloud4s.firestore

import cats.Eq
import cats.Show
import cats.data.NonEmptyChain
import cats.parse.Parser
import cats.parse.Parser0
import cats.syntax.all._
import io.circe.Decoder
import io.circe.Encoder
import me.wojnowski.googlecloud4s.ProjectId

sealed trait Reference extends Product with Serializable {
  def full: String = segments.mkString_("/")

  def segments: NonEmptyChain[String]

  def contains(other: Reference): Boolean

  def root: Reference.Root
}

object Reference {

  sealed trait NonCollection extends Reference {
    def collection(collectionId: CollectionId): Reference.Collection = Reference.Collection(this, collectionId)

    def /(collectionId: CollectionId): Reference.Collection = collection(collectionId)
  }

  object NonCollection {
    implicit val show: Show[Reference.NonCollection] = Show.show(_.full)
  }

  case class Root(projectId: ProjectId, databaseId: DatabaseId) extends NonCollection {
    def segments: NonEmptyChain[String] = NonEmptyChain.of("projects", projectId.value, "databases", databaseId.value, "documents")

    override def contains(other: Reference): Boolean =
      other =!= this

    override def toString: String = full

    override def root: Root = this
  }

  object Root {
    implicit val eq: Eq[Reference.Root] = Eq.fromUniversalEquals
  }

  case class Document(parent: Reference.Collection, documentId: DocumentId) extends NonCollection {
    def segments: NonEmptyChain[String] = parent.segments :+ documentId.value

    override def toString: String = full

    override def contains(other: Reference): Boolean =
      (other == this) || parent.contains(other)

    override def root: Root = parent.root
  }

  object Document {

    def parse(raw: String): Either[ParsingError, Reference.Document] =
      Reference.parse(raw).flatMap {
        case reference: Reference.Document => Right(reference)
        case _                             => Left(new ParsingError("expected non-root path"))
      }

    implicit val documentReferenceDecoder: Decoder[Reference.Document] =
      Decoder[String].emap(Reference.Document.parse(_).leftMap(_.getMessage))

    implicit val documentReferenceEncoder: Encoder[Reference.Document] =
      Encoder[String].contramap(_.full)

    implicit val ordering: Ordering[Reference.Document] = Ordering.by(_.full)

    implicit val show: Show[Reference.Document] = Show.show(_.full)

    implicit val eq: Eq[Reference.Document] = Eq.fromUniversalEquals

  }

  case class Collection(parent: Reference.NonCollection, collectionId: CollectionId) extends Reference {
    def segments: NonEmptyChain[String] = parent.segments :+ collectionId.value

    override def contains(other: Reference): Boolean =
      (other == this) || parent.contains(other)

    def document(documentId: DocumentId): Reference.Document = Reference.Document(this, documentId)

    def /(documentId: DocumentId): Reference.Document = document(documentId)

    override def root: Root =
      parent match {
        case root: Root           => root
        case collection: Document => collection.root
      }

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
      databaseId          <- DatabaseId.Parsing.parser
      _                   <- Parser.char('/')
      _                   <- Parser.string("documents")
      collectionNamePairs <- (CollectionId.parser.surroundedBy(Parser.char('/')) ~ DocumentId.parser).rep0
      maybeCollection     <- (Parser.char('/') *> CollectionId.parser).?
      _                   <- Parser.end
    } yield {
      val lastNonCollectionReference =
        collectionNamePairs.foldLeft[Reference.NonCollection](Root(ProjectId(projectId), databaseId)) {
          case (reference, (collectionId, documentName)) => Document(Collection(reference, collectionId), documentName)
        }

      maybeCollection match {
        case Some(collectionId) => lastNonCollectionReference.collection(collectionId)
        case None               => lastNonCollectionReference
      }
    }

  def parse(raw: String): Either[ParsingError, Reference] = parser.parseAll(raw).leftMap(error => new ParsingError(error.toString))

  type ParsingError = IllegalArgumentException
}
