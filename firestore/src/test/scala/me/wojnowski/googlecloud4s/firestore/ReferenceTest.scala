package me.wojnowski.googlecloud4s.firestore

import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString
import munit.FunSuite

class ReferenceTest extends FunSuite {
  test("Root") {
    val rawName = "projects/project-id/databases/(default)/documents"
    val result = Reference.parse(rawName)
    val expected = Reference.Root(ProjectId("project-id"))
    assertEquals(result, Right(expected))
  }

  test("Without subcollections") {
    val rawName = "projects/project-id/databases/(default)/documents/root-collection/Gh5Efo66qP8fRg4L2fuY"
    val result = Reference.parse(rawName)
    val expected =
      Reference.Document(
        Reference.Collection(Reference.Root(ProjectId("project-id")), "root-collection".toCollectionId),
        "Gh5Efo66qP8fRg4L2fuY".toDocumentId
      )
    assertEquals(result, Right(expected))
  }

  test("Without document at the end (root collection)") {
    val rawName = "projects/project-id/databases/(default)/documents/root-collection"
    val result = Reference.parse(rawName)
    assert(result.isLeft)
  }

  test("Without document at the end (subcollection)") {
    val rawName = "projects/project-id/databases/(default)/documents/collection-a/document-a/collection-b"
    val result = Reference.parse(rawName)
    assert(result.isLeft)
  }

  test("With subcollection") {
    val rawName =
      "projects/project-id/databases/(default)/documents/collection-a/document-a/collection-b/document-b/collection-c/document-c"
    val result = Reference.parse(rawName)
    val expected =
      Reference.Document(
        Reference.Collection(
          Reference.Document(
            Reference.Collection(
              Reference.Document(
                Reference.Collection(
                  Reference.Root(ProjectId("project-id")),
                  "collection-a".toCollectionId
                ),
                "document-a".toDocumentId
              ),
              "collection-b".toCollectionId
            ),
            "document-b".toDocumentId
          ),
          "collection-c".toCollectionId
        ),
        "document-c".toDocumentId
      )
    assertEquals(result, Right(expected))
  }

  test("Slash in front") {
    val rawName = "/projects/project-id/databases/(default)/documents/root-collection/root-a/nested-collection/Gh5Efo66qP8fRg4L2fuY"
    val result = Reference.parse(rawName)
    assert(result.isLeft)
  }

  test("Slash at the end is invalid") {
    val rawName = "projects/project-id/databases/(default)/documents/root-collection/root-a/nested-collection/Gh5Efo66qP8fRg4L2fuY/"
    val result = Reference.parse(rawName)
    assert(result.isLeft)
  }

  test("Root toString") {
    val path = Reference.Root(ProjectId("project-id"))
    val result = path.full
    val expected = "projects/project-id/databases/(default)/documents"
    assertEquals(result, expected)
  }

  test("Path.Document toString") {
    val path = Reference.Document(
      Reference.Collection(
        Reference.Document(
          Reference.Collection(
            Reference.Document(
              Reference.Collection(
                Reference.Root(ProjectId("project-id")),
                "collection-a".toCollectionId
              ),
              "document-a".toDocumentId
            ),
            "collection-b".toCollectionId
          ),
          "document-b".toDocumentId
        ),
        "collection-c".toCollectionId
      ),
      "document-c".toDocumentId
    )

    val result = path.full
    val expected =
      "projects/project-id/databases/(default)/documents/collection-a/document-a/collection-b/document-b/collection-c/document-c"

    assertEquals(result, expected)
  }

  test("Resolving root") {
    val collectionId = "collection-a".toCollectionId
    val documentId = "document-a".toDocumentId

    val path = Reference.Root(ProjectId("project-id"))

    val result = path.collection(collectionId).document(documentId)
    val expected = Reference.Document(Reference.Collection(path, collectionId), documentId)
    assertEquals(result, expected)
  }

  test("Resolving document") {
    val collectionAId = "collection-a".toCollectionId
    val documentAId = "document-a".toDocumentId
    val collectionBId = "collection-a".toCollectionId
    val documentBId = "document-a".toDocumentId

    val rootPath = Reference.Root(ProjectId("project-id"))
    val path = Reference.Document(Reference.Collection(rootPath, collectionAId), documentAId)

    val result = path.collection(collectionBId).document(documentBId)
    val expected =
      Reference.Document(
        Reference.Collection(
          Reference.Document(
            Reference.Collection(rootPath, collectionAId),
            documentAId
          ),
          collectionBId
        ),
        documentBId
      )
    assertEquals(result, expected)
  }

  test("Contains partial") {}

}
