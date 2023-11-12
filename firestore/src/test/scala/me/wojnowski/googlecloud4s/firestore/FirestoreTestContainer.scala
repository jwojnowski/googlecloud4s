package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import cats.syntax.all._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.TokenProvider
import munit.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

trait FirestoreTestContainer extends TestContainerForAll with TestContainerUtils {
  this: FunSuite =>

  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  val projectId: ProjectId = ProjectId("project-id")
  val databaseId: DatabaseId = DatabaseId.unsafe("database-id")

  override def extractUri: FirestoreEmulatorContainer => String = _.uri

  def withFirestore(f: Firestore[IO] => IO[Unit]): IO[Unit] =
    withContainerUri { uri =>
      withSttpBackend { backend =>
        f(Firestore.instance[IO](backend, projectId, databaseId, uri.some))
      }
    }

}
