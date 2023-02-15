package me.wojnowski.googlecloud4s.firestore

import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter
import me.wojnowski.googlecloud4s.firestore.Firestore.Order
import fs2.Stream
import Firestore.Error
import cats.data.NonEmptyList
import cats.data.NonEmptyMap

object FirestoreDsl {

  implicit class FirestoreOps[F[_]](firestore: Firestore[F]) {
    def collection(collectionId: CollectionId): CollectionReferenceWithFirestore[F] =
      CollectionReferenceWithFirestore(firestore.rootReference.collection(collectionId), firestore)

    def /(collectionId: CollectionId): CollectionReferenceWithFirestore[F] = collection(collectionId)
  }

  case class CollectionReferenceWithFirestore[F[_]](collection: Reference.Collection, firestore: Firestore[F])
    extends CollectionReferenceOps[F] {
    def document(documentId: DocumentId): DocumentReferenceWithFirestore[F] =
      DocumentReferenceWithFirestore(collection.document(documentId), firestore)

    def /(documentId: DocumentId): DocumentReferenceWithFirestore[F] = document(documentId)
  }

  case class DocumentReferenceWithFirestore[F[_]](document: Reference.Document, firestore: Firestore[F]) extends DocumentReferenceOps[F] {
    def collection(collectionId: CollectionId): CollectionReferenceWithFirestore[F] =
      CollectionReferenceWithFirestore(document.collection(collectionId), firestore)

    def /(collectionId: CollectionId): CollectionReferenceWithFirestore[F] = collection(collectionId)
  }

  implicit class ImplicitCollectionReferenceOps[F[_]: Firestore](val collection: Reference.Collection) extends CollectionReferenceOps[F] {
    def firestore: Firestore[F] = Firestore[F]
  }

  implicit class ImplicitDocumentReferenceOps[F[_]: Firestore](val document: Reference.Document) {
    def firestore: Firestore[F] = Firestore[F]
  }

  trait CollectionReferenceOps[F[_]] {

    def collection: Reference.Collection

    def firestore: Firestore[F]

    def add[V: FirestoreCodec](value: V): F[Reference.Document] = firestore.add(collection, value)

    def stream[V: FirestoreCodec](
      filters: List[FieldFilter] = List.empty,
      orderBy: List[Order] = List.empty,
      pageSize: Int = 50
    ): Stream[F, (Reference.Document, Either[Error.DecodingFailure, V])] =
      firestore.stream(collection, filters, orderBy, pageSize)

    def streamLogFailures[V: FirestoreCodec](
      filters: List[FieldFilter] = List.empty,
      orderBy: List[Order] = List.empty,
      pageSize: Int = 50
    ): Stream[F, (Reference.Document, V)] =
      firestore.streamLogFailures(collection, filters, orderBy, pageSize)

    def batchGet[V: FirestoreCodec](ids: NonEmptyList[DocumentId]): F[NonEmptyMap[Reference.Document, Option[V]]] =
      firestore.batchGet(ids.map(id => collection.document(id)))

  }

  trait DocumentReferenceOps[F[_]] {
    def document: Reference.Document
    def firestore: Firestore[F]

    def set[V: FirestoreCodec](value: V): F[Unit] = firestore.set(document, value)

    def get[V: FirestoreCodec]: F[Option[V]] = firestore.get(document)

    def update[V: FirestoreCodec](f: V => V): F[Option[V]] = firestore.update(document, f)

    def delete: F[Unit] = firestore.delete(document)
  }

  implicit class BatchGetOps[F[_]](references: NonEmptyList[Reference.Document])(implicit firestore: Firestore[F]) {
    def batchGet[V: FirestoreCodec]: F[NonEmptyMap[Reference.Document, Option[V]]] = firestore.batchGet(references)
  }

}
