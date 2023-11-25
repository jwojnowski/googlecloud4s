package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString
import munit.CatsEffectSuite
import FirestoreDsl._
import cats.implicits._
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

class CollectionListTest extends CatsEffectSuite with FirestoreTestContainer {
  override def munitTimeout: Duration = 1.minute

  val document = Fields("foo" -> Value.Null)

  val collectionA = Reference.Root(projectId, databaseId) / "collection-a".toCollectionId
  val collectionB = Reference.Root(projectId, databaseId) / "collection-b".toCollectionId
  val collectionC = Reference.Root(projectId, databaseId) / "collection-c".toCollectionId
  val collectionD = Reference.Root(projectId, databaseId) / "collection-d".toCollectionId
  val collectionE = Reference.Root(projectId, databaseId) / "collection-e".toCollectionId

  val documentAName = "document-a".toDocumentId
  val documentBName = "document-b".toDocumentId

  val collectionAA = collectionA / documentAName / "collection-aa".toCollectionId
  val collectionAB = collectionA / documentAName / "collection-ab".toCollectionId

  val rootLevelCollections = List(collectionA, collectionB, collectionC, collectionD, collectionE)
  val documentLevelCollections = List(collectionAA, collectionAB)

  import me.wojnowski.googlecloud4s.firestore.codec.circe._

  test("no collections to list (root level)") {
    withFirestore { implicit firestore =>
      for {
        collectionIds <- (firestore / projectId / databaseId).listCollections().compile.toList
      } yield assertEquals(collectionIds, List.empty)
    }
  }

  test("no collections to list (document level)") {
    withFirestore { implicit firestore =>
      for {
        _             <- (collectionA / documentAName).set(document)
        collectionIds <- (collectionA / documentAName).listCollections().compile.toList
      } yield assertEquals(collectionIds, List.empty)
    }
  }

  test("collections at the root level") {
    withFirestore { implicit firestore =>
      for {
        _             <- createCollections
        collectionIds <- (firestore / projectId / databaseId).listCollections().compile.toList
      } yield assertEquals(collectionIds, rootLevelCollections.map(_.collectionId))
    }
  }

  test("multiple pages of collections") {
    withFirestore { implicit firestore =>
      for {
        _             <- createCollections
        collectionIds <- (firestore / projectId / databaseId).listCollections(pageSize = Some(2)).compile.toList
      } yield assertEquals(collectionIds, rootLevelCollections.map(_.collectionId))
    }
  }

  test("subcollections") {
    withFirestore { implicit firestore =>
      for {
        _             <- createCollections
        collectionIds <- collectionAA.parent.listCollections().compile.toList
      } yield assertEquals(collectionIds, documentLevelCollections.map(_.collectionId))
    }
  }

  private def createCollections(implicit firestore: Firestore[IO]) =
    (rootLevelCollections ++ documentLevelCollections).traverse(collectionReference => (collectionReference / documentAName).set(document))

}
