package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.TokenProvider
import munit.CatsEffectSuite

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import cats.syntax.all._
import io.circe.Decoder
import io.circe.Encoder
import me.wojnowski.googlecloud4s.firestore.Firestore.Collection
import me.wojnowski.googlecloud4s.firestore.OptimisticLockingTest.TestDocument
import sttp.client3.testing.RecordingSttpBackend

/* Firestore emulator always fails with:
   > the stored version (1659473498689424) does not match the required base version (0)
   so there's not too much one can test with it in regards to optimistic locking.
 */
class OptimisticLockingTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {
  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  val projectId: ProjectId = ProjectId("project-id")

  val collection = Collection("collection-a")

  import FirestoreCodec.circe._

  test("updates fail after exhausting configured attempts") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val recordingBackend = new RecordingSttpBackend(backend)

        val attempts = 3

        val firestore = Firestore.instance[IO](recordingBackend, projectId, uri.some, optimisticLockingAttempts = attempts)

        for {
          name   <- firestore.add(collection, TestDocument(counter = 0)).map(Firestore.Name.Short.apply)
          result <- firestore.update[TestDocument](collection, name, document => document.copy(document.counter + 1)).attempt
          patchRequestsCount = recordingBackend.allInteractions.count {
                                 case (request, _) => request.method == sttp.model.Method.PATCH
                               }
        } yield assert(patchRequestsCount == attempts && result == Left(Firestore.Error.OptimisticLockingFailure))
      }
    }
  }

  override def extractUri: FirestoreEmulatorContainer => String = _.uri
}

object OptimisticLockingTest {
  case class TestDocument(counter: Int)

  implicit val encoder: Encoder[TestDocument] = Encoder.forProduct1("counter")(_.counter)
  implicit val decoder: Decoder[TestDocument] = Decoder.forProduct1("counter")(TestDocument.apply)

}
