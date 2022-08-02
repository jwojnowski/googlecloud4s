package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import cats.syntax.all._
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.EncoderOps
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore.Collection
import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter
import me.wojnowski.googlecloud4s.firestore.Firestore.Order

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

// TODO add a set of meaningful tests
class StreamingTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {

  override def extractUri: FirestoreEmulatorContainer => String = _.uri

  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  val projectId: ProjectId = ProjectId("project-id")

  import FirestoreCodec.circe._

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  test("Cursors with orderBy parameters") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        val tens = List.fill(10)(JsonObject("foo" -> "15".asJson).asJson)
        val elevens = List.fill(10)(JsonObject("foo" -> "16".asJson).asJson)
        val items = tens ++ elevens

        for {
          _       <- items.traverse(Firestore[IO].add[Json](Collection("test-collection"), _))
          results <- Firestore[IO]
                       .stream[Json](
                         Collection("test-collection"),
                         filters = List(
                           FieldFilter("foo", "14", FieldFilter.Operator.>),
                           FieldFilter("foo", "16", FieldFilter.Operator.<)
                         ),
                         orderBy = List(
                           Order("foo", Order.Direction.Descending)
                         ),
                         pageSize = 3
                       )
                       .compile
                       .toList
        } yield assertEquals(results.flatMap(_._2.toOption), tens)

      }
    }
  }

}
