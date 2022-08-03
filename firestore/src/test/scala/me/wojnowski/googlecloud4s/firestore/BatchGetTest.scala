package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import me.wojnowski.googlecloud4s.TestContainerUtils
import munit.CatsEffectSuite
import cats.syntax.all._
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore.Collection
import me.wojnowski.googlecloud4s.firestore.Firestore.Name

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

class BatchGetTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {
  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  val projectId: ProjectId = ProjectId("project-id")

  val collection = Collection("collection-a")

  import FirestoreCodec.circe._

  val documentAName = Name.Short("document-a")
  val documentCName = Name.Short("document-c")
  val nonExistentDocumentName = Name.Short("idontexist")

  test("batchGet supports both short and full names") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          _             <- firestore.put(collection, documentAName, TestDocumentWithCounter(counter = 0))
          documentBName <- firestore.add(collection, TestDocumentWithCounter(counter = 0))
          results       <- firestore.batchGet[TestDocumentWithCounter](
                             collection,
                             NonEmptyList.of(documentAName, documentBName)
                           )
        } yield assertEquals(
          results.toSortedMap.view.mapValues(_.nonEmpty).toMap,
          Map(
            documentAName.toFull(projectId, collection) -> true,
            documentBName -> true
          )
        )
      }
    }
  }

  test("batchGet returns full names even for non-existent documents") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val attempts = 3

        val firestore = Firestore.instance[IO](backend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          _       <- firestore.put(collection, documentAName, TestDocumentWithCounter(counter = 0))
          results <- firestore.batchGet[TestDocumentWithCounter](
                       collection,
                       NonEmptyList.of(documentAName, nonExistentDocumentName)
                     )
        } yield assertEquals(
          results.toSortedMap.view.mapValues(_.nonEmpty).toMap,
          Map(
            documentAName.toFull(projectId, collection) -> true,
            nonExistentDocumentName.toFull(projectId, collection) -> false
          )
        )
      }
    }
  }

  override def extractUri: FirestoreEmulatorContainer => String = _.uri
}
