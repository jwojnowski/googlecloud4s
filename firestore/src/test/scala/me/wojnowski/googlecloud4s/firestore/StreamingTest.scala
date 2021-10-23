package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import cats.effect.std.Dispatcher
import com.dimafeng.testcontainers.munit.TestContainerForAll
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uri
import munit.CatsEffectSuite
import cats.syntax.all._
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.EncoderOps
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.Scope
import me.wojnowski.googlecloud4s.auth.Token
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore.Collection
import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter
import me.wojnowski.googlecloud4s.firestore.Firestore.Order
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

class StreamingTest extends CatsEffectSuite with TestContainerForAll {

  override def munitTimeout: Duration = 1.minute

  val containerDef = FirestoreEmulatorContainer.Def()

  val projectId = ProjectId("whatever")

  import FirestoreCodec.circe._

  implicit val tokenProvider = new TokenProvider[IO] {

    override def getToken(scope: Scope): IO[Token] =
      IO.pure(
        Token(
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJsb2dnZWRJbkFzIjoiYWRtaW4iLCJpYXQiOjE0MjI3Nzk2Mzh9.gzSraSYS8EXBxLN_oWnFSRgCzcmJmMjLiuyu5CSpyHI",
          Scope("test"),
          Instant.EPOCH
        )
      )

  }

  test("No entries") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId)(uri.some)

//        val items = (1 to 10).map(n => JsonObject("foo" -> "10".asJson).asJson).toList
        val tens = List.fill(10)(JsonObject("foo" -> "15".asJson).asJson)
        val elevens = List.fill(10)(JsonObject("foo" -> "16".asJson).asJson)
        val items = tens ++ elevens

        for {
          _       <- items.traverse(Firestore[IO].add[Json](Collection("test-collection"), _))
          results <- Firestore[IO]
                       .stream[Json](
                         Collection("test-collection") /*, orderBy = List(Firestore.Order("foo", Firestore.Order.Direction.Descending))*/,
                         filters = List(
                           FieldFilter("foo", "14", FieldFilter.Operator.>),
                           FieldFilter("foo", "16", FieldFilter.Operator.<),
                         ),
                         orderBy = List(
                           Order("foo", Order.Direction.Ascending)
                         ),
                         pageSize = 3
                       )
                       .compile
                       .toList
        } yield assertEquals(results.map(_._2).sequence.getOrElse(List.empty), tens) // TODO this could be better

      }
    }
  }

  // TODO these are copy-pasted
  private def withSttpBackend[A](f: SttpBackend[IO, Fs2Streams[IO]] => IO[A]): IO[A] =
    Dispatcher[IO].use { dispatcher =>
      HttpClientFs2Backend(dispatcher).flatMap(f)
    }

  private def withContainerUri[A](f: String Refined Uri => IO[A]) =
    withContainers { containers =>
      IO.fromEither(refineV[Uri]("http://" + containers.container.getEmulatorEndpoint).leftMap(new IllegalArgumentException(_))).flatMap(f)
    }

}
