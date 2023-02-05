package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import me.wojnowski.googlecloud4s.TestContainerUtils
import munit.CatsEffectSuite
import cats.syntax.all._
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

class BatchGetTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {
  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  val projectId: ProjectId = ProjectId("project-id")

  val collectionA = "collection-a".toCollectionId
  val collectionB = "collection-b".toCollectionId

  import FirestoreCodec.circe._

  val documentAName = "document-a".toDocumentId
  val documentCName = "document-c".toDocumentId
  val nonExistentDocumentName = "idontexist".toDocumentId

  test("batchGet supports document IDs") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          _                  <- firestore.put(collectionA, documentAName, TestDocumentWithCounter(counter = 0))
          documentBReference <- firestore.add(collectionA, TestDocumentWithCounter(counter = 0))
          results            <- firestore.batchGet[TestDocumentWithCounter](
                                  collectionA,
                                  NonEmptyList.of(documentAName, documentBReference.documentId)
                                )
        } yield assertEquals(
          results.toSortedMap.view.mapValues(_.nonEmpty).toMap,
          Map(
            Reference.Root(projectId).resolve(collectionA, documentAName) -> true,
            documentBReference -> true
          )
        )
      }
    }
  }

  test("batchGet supports full document references with different collections") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          documentAReference <- firestore.add(collectionA, TestDocumentWithCounter(counter = 0))
          documentBReference <- firestore.add(collectionB, TestDocumentWithCounter(counter = 0))
          results            <- firestore.batchGet[TestDocumentWithCounter](
                                  NonEmptyList.of(documentAReference, documentBReference)
                                )
        } yield assertEquals(
          results.toSortedMap.view.mapValues(_.nonEmpty).toMap,
          Map(
            documentAReference -> true,
            documentBReference -> true
          )
        )
      }
    }
  }

  test("batchGet fails when references don't match project root") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        val referenceFromDifferentProject = Reference.Root(ProjectId("other-project-id")).resolve(collectionB, "document-b".toDocumentId)

        for {
          documentAReference <- firestore.add(collectionA, TestDocumentWithCounter(counter = 0))
          result             <- firestore
                                  .batchGet[TestDocumentWithCounter](
                                    NonEmptyList.of(documentAReference, referenceFromDifferentProject)
                                  )
                                  .attempt
        } yield assertEquals(
          result,
          Left(Firestore.Error.ReferencesDontMatchRoot(NonEmptyList.of(referenceFromDifferentProject), firestore.rootReference))
        )
      }
    }
  }

  test("batchGet returns full references even for non-existent documents") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          _       <- firestore.put(collectionA, documentAName, TestDocumentWithCounter(counter = 0))
          results <- firestore.batchGet[TestDocumentWithCounter](
                       collectionA,
                       NonEmptyList.of(documentAName, nonExistentDocumentName)
                     )
        } yield assertEquals(
          results.toSortedMap.view.mapValues(_.nonEmpty).toMap,
          Map(
            Reference.Root(projectId).resolve(collectionA, documentAName) -> true,
            Reference.Root(projectId).resolve(collectionA, nonExistentDocumentName) -> false
          )
        )
      }
    }
  }

  override def extractUri: FirestoreEmulatorContainer => String = _.uri
}
