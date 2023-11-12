package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Helpers._
import munit.CatsEffectSuite
import cats.syntax.all._

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import FirestoreDsl._
import io.circe.syntax.EncoderOps

class NonUrlSafeNamesTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {

  override def extractUri: FirestoreEmulatorContainer => String = _.uri

  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  val projectId: ProjectId = ProjectId("project-id")
  val databaseId: DatabaseId = DatabaseId.unsafe("database-id")

  import me.wojnowski.googlecloud4s.firestore.codec.circe._

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  test("Non-URL-safe characters are allowed") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val documentAPath =
          Reference
            .Root(projectId, databaseId)
            .collection("🤔".toCollectionId)
            .document("💡".toDocumentId)
            .collection("#".toCollectionId)
            .document("?".toDocumentId)

        val value = JsonObject("foo" -> "bar".asJson)

        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, databaseId, uri.some)

        for {
          items1 <- Firestore[IO].stream[JsonObject](documentAPath.parent).compile.toList
          _      <- documentAPath.set[JsonObject](value)
          items2 <- Firestore[IO].stream[JsonObject](documentAPath.parent).compile.toList
        } yield {
          assert(items1.isEmpty)
          assertEquals(items2.map(_._1), List(documentAPath))
          assertEquals(items2.map(_._2), List(Right(value)))
        }
      }
    }
  }
}
