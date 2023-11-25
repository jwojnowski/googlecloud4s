package me.wojnowski.googlecloud4s.firestore

import FirestoreDsl._
import cats.data.NonEmptyList
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString
import munit.CatsEffectSuite
import cats.effect.IO
import cats.implicits._

class CopyTest extends CatsEffectSuite with FirestoreTestContainer {
  val root = Reference.Root(projectId, databaseId)

  val databaseId2: DatabaseId = DatabaseId.unsafe("database-id-2")

  val collectionA = root / "collection-A".toCollectionId / "document-X".toDocumentId
  val collectionB =
    root / "collection-A".toCollectionId / "document-X".toDocumentId / "collection-A-X-a".toCollectionId / "document-A-X-a-x".toDocumentId
  val collectionC =
    root / "collection-A".toCollectionId / "document-X".toDocumentId / "collection-A-X-a".toCollectionId / "document-A-X-a-y".toDocumentId
  val collectionD =
    root / "collection-A".toCollectionId / "document-X".toDocumentId / "collection-A-X-a".toCollectionId / "document-A-X-a-z".toDocumentId
  val collectionE =
    root / "collection-A".toCollectionId / "document-Y".toDocumentId / "collection-A-Y-a".toCollectionId / "document-A-X-a-z".toDocumentId

  test("deep-level document") {
    withFirestore { implicit firestore =>
      val copy = Copy.instance[IO](firestore)

      val value = createDocument("XAYBZ")

      for {
        _             <- (root / "source-A".toCollectionId / "X".toDocumentId / "A".toCollectionId /
                             "Y".toDocumentId / "B".toCollectionId / "Z".toDocumentId).set(value)
        _             <- copy.copyRecursively(root / "source-A".toCollectionId, root / "target-A".toCollectionId)
        maybeDocument <- (root / "target-A".toCollectionId / "X".toDocumentId / "A".toCollectionId /
                             "Y".toDocumentId / "B".toCollectionId / "Z".toDocumentId).get[Fields]
      } yield assertEquals(maybeDocument, Some(value))
    }

  }

  test("multiple collections at root level") {
    withFirestore { implicit firestore =>
      val copy = Copy.instance[IO](firestore)

      val target = root.copy(databaseId = databaseId2)

      for {
        _                <- (root / "A".toCollectionId / "X".toDocumentId).set(createDocument("AX"))
        _                <- (root / "B".toCollectionId / "X".toDocumentId).set(createDocument("BX"))
        _                <- (root / "C".toCollectionId / "X".toDocumentId).set(createDocument("CX"))
        _                <- copy.copyCollectionsRecursively(root, target)
        batchGetResponse <- target.batchGet[Fields](
                              NonEmptyList.of(
                                target / "A".toCollectionId / "X".toDocumentId,
                                target / "B".toCollectionId / "X".toDocumentId,
                                target / "C".toCollectionId / "X".toDocumentId
                              )
                            )
      } yield assertEquals(
        batchGetResponse.toNel.collect { case (_, Some(fields)) => fields },
        List(
          createDocument("AX"),
          createDocument("BX"),
          createDocument("CX")
        )
      )
    }
  }

  test("multiple collections at document level") {
    withFirestore { implicit firestore =>
      val copy = Copy.instance[IO](firestore)

      val source = root / "source-collection".toCollectionId / "source-document".toDocumentId
      val target = root / "target-collection".toCollectionId / "target-document".toDocumentId

      for {
        _                <- (source / "A".toCollectionId / "X".toDocumentId).set(createDocument("AX"))
        _                <- (source / "B".toCollectionId / "X".toDocumentId).set(createDocument("BX"))
        _                <- (source / "C".toCollectionId / "X".toDocumentId).set(createDocument("CX"))
        _                <- copy.copyCollectionsRecursively(source, target)
        batchGetResponse <- target.batchGet[Fields](
                              NonEmptyList.of(
                                target / "A".toCollectionId / "X".toDocumentId,
                                target / "B".toCollectionId / "X".toDocumentId,
                                target / "C".toCollectionId / "X".toDocumentId
                              )
                            )
      } yield assertEquals(
        batchGetResponse.toNel.collect { case (_, Some(fields)) => fields },
        List(
          createDocument("AX"),
          createDocument("BX"),
          createDocument("CX")
        )
      )
    }
  }

  test("multiple documents in collection at root level") {
    withFirestore { implicit firestore =>
      val copy = Copy.instance[IO](firestore)

      val target = root.copy(databaseId = databaseId2)

      for {
        _                <- (root / "A".toCollectionId / "X".toDocumentId).set(createDocument("AX"))
        _                <- (root / "A".toCollectionId / "Y".toDocumentId).set(createDocument("AY"))
        _                <- (root / "A".toCollectionId / "Z".toDocumentId).set(createDocument("AZ"))
        _                <- copy.copyCollectionsRecursively(root, target)
        batchGetResponse <- target.batchGet[Fields](
                              NonEmptyList.of(
                                target / "A".toCollectionId / "X".toDocumentId,
                                target / "A".toCollectionId / "Y".toDocumentId,
                                target / "A".toCollectionId / "Z".toDocumentId
                              )
                            )
      } yield assertEquals(
        batchGetResponse.toNel.collect { case (_, Some(fields)) => fields },
        List(
          createDocument("AX"),
          createDocument("AY"),
          createDocument("AZ")
        )
      )
    }
  }

  test("multiple documents in collection at document level") {
    withFirestore { implicit firestore =>
      val copy = Copy.instance[IO](firestore)

      val source = root / "source-collection".toCollectionId / "source-document".toDocumentId
      val target = root / "target-collection".toCollectionId / "target-document".toDocumentId

      for {
        _                <- (source / "A".toCollectionId / "X".toDocumentId).set(createDocument("AX"))
        _                <- (source / "A".toCollectionId / "Y".toDocumentId).set(createDocument("AY"))
        _                <- (source / "A".toCollectionId / "Z".toDocumentId).set(createDocument("AZ"))
        _                <- copy.copyCollectionsRecursively(source, target)
        batchGetResponse <- target.batchGet[Fields](
                              NonEmptyList.of(
                                target / "A".toCollectionId / "X".toDocumentId,
                                target / "A".toCollectionId / "Y".toDocumentId,
                                target / "A".toCollectionId / "Z".toDocumentId
                              )
                            )
      } yield assertEquals(
        batchGetResponse.toNel.collect { case (_, Some(fields)) => fields },
        List(
          createDocument("AX"),
          createDocument("AY"),
          createDocument("AZ")
        )
      )
    }
  }

  test("more documents than chunk size") {
    withFirestore { implicit firestore =>
      val sourceCollection = firestore / projectId / databaseId / "collection-A".toCollectionId
      val targetCollection = firestore / projectId / databaseId / "collection-B".toCollectionId
      val copy = Copy.instance[IO](firestore)

      val chunkSize = 3
      val documentIds = (0 to 7)
        .map { i =>
          ('A' + i).toChar.toString.toDocumentId
        }
        .toList
        .toNel
        .get

      for {
        _                 <- sourceCollection.batchWrite {
                               documentIds
                                 .map { documentId =>
                                   Write.Update(
                                     (sourceCollection / documentId).reference,
                                     createDocument(documentId.value)
                                   )
                                 }
                             }
        _                 <- copy.copyRecursively(
                               sourceCollection.reference,
                               targetCollection.reference,
                               chunkSize = chunkSize
                             )
        copiedDocumentIds <- targetCollection.streamLogFailures[Fields]().map(_._1.documentId).compile.toList
      } yield assertEquals(documentIds.toList.toSet, copiedDocumentIds.toSet)
    }
  }

  test("copy fails when the target is in source and read time isn't provided (collection)") {
    withFirestore { implicit firestore =>
      val source = (firestore / projectId / databaseId / "collection-A".toCollectionId).reference
      val target =
        (firestore / projectId / databaseId / "collection-A".toCollectionId / "document-X".toDocumentId / "collection-B".toCollectionId).reference

      val copy = Copy.instance[IO](firestore)

      for {
        result <- copy.copyRecursively(source, target).attempt
      } yield assert(result.isLeft)
    }
  }

  test("copy fails when the target is in source and read time isn't provided (non-collection)") {
    withFirestore { implicit firestore =>
      val source = (firestore / projectId / databaseId).reference
      val target = (firestore / projectId / databaseId / "collection-A".toCollectionId / "document-X".toDocumentId).reference

      val copy = Copy.instance[IO](firestore)

      for {
        result <- copy.copyCollectionsRecursively(source, target).attempt
      } yield assert(result.isLeft)
    }
  }

  private def createDocument(value: String) = Fields("foo" -> Value.String(value))
}
