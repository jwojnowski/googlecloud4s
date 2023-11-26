package me.wojnowski.googlecloud4s.firestore

import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter
import me.wojnowski.googlecloud4s.firestore.Firestore.Order
import fs2.Stream
import Firestore.Error
import cats.data.NonEmptyList
import cats.data.NonEmptyMap
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec

import java.time.Instant

object FirestoreDsl {

  implicit class FirestoreOps[F[_]](firestore: Firestore[F]) {
    def projectId(projectId: ProjectId): ProjectReferenceWithFirestore[F] = ProjectReferenceWithFirestore(projectId, firestore)

    def /(projectId: ProjectId): ProjectReferenceWithFirestore[F] = this.projectId(projectId)
  }

  case class ProjectReferenceWithFirestore[F[_]](projectId: ProjectId, firestore: Firestore[F]) {
    def databaseId(databaseId: DatabaseId): RootReferenceWithFirestore[F] = RootReferenceWithFirestore(projectId, databaseId, firestore)
    def /(databaseId: DatabaseId): RootReferenceWithFirestore[F] = this.databaseId(databaseId)
  }

  case class RootReferenceWithFirestore[F[_]](projectId: ProjectId, databaseId: DatabaseId, firestore: Firestore[F])
    extends NonCollectionReferenceOps[F] {
    override def reference: Reference.NonCollection = Reference.Root(projectId, databaseId)
  }

  case class CollectionReferenceWithFirestore[F[_]](reference: Reference.Collection, firestore: Firestore[F])
    extends CollectionReferenceOps[F] {
    def document(documentId: DocumentId): DocumentReferenceWithFirestore[F] =
      DocumentReferenceWithFirestore(reference.document(documentId), firestore)

    def /(documentId: DocumentId): DocumentReferenceWithFirestore[F] = document(documentId)
  }

  case class DocumentReferenceWithFirestore[F[_]](reference: Reference.Document, firestore: Firestore[F]) extends DocumentReferenceOps[F] {
    def collection(collectionId: CollectionId): CollectionReferenceWithFirestore[F] =
      CollectionReferenceWithFirestore(reference.collection(collectionId), firestore)

    def /(collectionId: CollectionId): CollectionReferenceWithFirestore[F] = collection(collectionId)
  }

  implicit class ImplicitCollectionReferenceOps[F[_]: Firestore](val reference: Reference.Collection) extends CollectionReferenceOps[F] {
    def firestore: Firestore[F] = Firestore[F]
  }

  implicit class ImplicitDocumentReferenceOps[F[_]: Firestore](val reference: Reference.Document) extends DocumentReferenceOps[F] {
    def firestore: Firestore[F] = Firestore[F]
  }

  implicit class ImplicitNonCollectionReferenceOps[F[_]: Firestore](val reference: Reference.NonCollection)
    extends NonCollectionReferenceOps[F] {
    def firestore: Firestore[F] = Firestore[F]
  }

  trait CollectionReferenceOps[F[_]] {

    def reference: Reference.Collection

    def firestore: Firestore[F]

    def add[V: FirestoreCodec](value: V): F[Reference.Document] = firestore.add(reference, value)

    def stream[V: FirestoreCodec](
      filters: List[FieldFilter] = List.empty,
      orderBy: List[Order] = List.empty,
      pageSize: Int = 50
    ): Stream[F, (Reference.Document, Either[Error.DecodingFailure, V])] =
      firestore.stream(reference, filters, orderBy, pageSize)

    def streamLogFailures[V: FirestoreCodec](
      filters: List[FieldFilter] = List.empty,
      orderBy: List[Order] = List.empty,
      pageSize: Int = 50
    ): Stream[F, (Reference.Document, V)] =
      firestore.streamLogFailures(reference, filters, orderBy, pageSize)

    def batchGet[V: FirestoreCodec](ids: NonEmptyList[DocumentId]): F[NonEmptyMap[Reference.Document, Option[V]]] =
      firestore.batchGet(reference.root, ids.map(id => reference.document(id)))

    def batchWrite(writes: NonEmptyList[Write], labels: Map[String, String] = Map.empty): F[BatchWriteResponse] =
      firestore.batchWrite(reference.root, writes, labels)

  }

  trait DocumentReferenceOps[F[_]] {
    def reference: Reference.Document
    def firestore: Firestore[F]

    def set[V: FirestoreCodec](value: V): F[Unit] = firestore.set(reference, value)

    def get[V: FirestoreCodec]: F[Option[V]] = firestore.get(reference)

    def update[V: FirestoreCodec](f: V => V): F[Option[V]] = firestore.update(reference, f)
    def updateM[V: FirestoreCodec](f: V => F[V]): F[Option[V]] = firestore.updateM(reference, f)

    def delete: F[Unit] = firestore.delete(reference)
  }

  trait NonCollectionReferenceOps[F[_]] {
    def reference: Reference.NonCollection
    def firestore: Firestore[F]

    def listCollectionIds(pageSize: Option[Int] = None, readTime: Option[Instant] = None): Stream[F, CollectionId] =
      firestore.listCollectionIds(reference, pageSize, readTime)

    def batchGet[V: FirestoreCodec](references: NonEmptyList[Reference.Document]): F[NonEmptyMap[Reference.Document, Option[V]]] =
      firestore.batchGet(reference.root, references)

    def batchWrite(writes: NonEmptyList[Write], labels: Map[String, String] = Map.empty): F[BatchWriteResponse] =
      firestore.batchWrite(reference.root, writes, labels)

    def collection(collectionId: CollectionId): CollectionReferenceWithFirestore[F] =
      CollectionReferenceWithFirestore(reference.collection(collectionId), firestore)

    def /(collectionId: CollectionId): CollectionReferenceWithFirestore[F] = collection(collectionId)
  }

}
