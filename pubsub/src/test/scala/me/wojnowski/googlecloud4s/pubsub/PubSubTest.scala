package me.wojnowski.googlecloud4s.pubsub

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.syntax.all._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uri
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.AccessToken
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.auth.TokenProvider
import munit.CatsEffectSuite
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

import java.time.Instant

class PubSubTest extends CatsEffectSuite with TestContainerForAll {

  val containerDef: PubSubEmulatorContainer.Def = PubSubEmulatorContainer.Def()

  val topic = Topic("test-topic")
  val projectId = ProjectId("test-project")

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.const(AccessToken("test", Scopes("test"), Instant.EPOCH))

  test("Publishing to non-existent topic results in an error") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val pubSub = PubSub.instance(projectId, backend, uriOverride = uri.some)
        val message =
          OutgoingMessage("data")

        for {
          result <- pubSub.publish(topic, message).attempt
        } yield assertEquals(result, Left(PubSub.Error.TopicNotFound))
      }
    }

  }

  test("Publishing to existent topic") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val pubSub = PubSub.instance(projectId, backend, uriOverride = uri.some)
        val message = OutgoingMessage("data")

        for {
          _      <- pubSub.createTopic(topic)
          result <- pubSub.publish(topic, message).attempt
        } yield assert(result.isRight)
      }
    }

  }

  private def withSttpBackend[A](f: SttpBackend[IO, Fs2Streams[IO]] => IO[A]): IO[A] =
    Dispatcher[IO].use { dispatcher =>
      HttpClientFs2Backend(dispatcher).flatMap(f)
    }

  private def withContainerUri[A](f: String Refined Uri => IO[A]) =
    withContainers { containers =>
      IO.fromEither(refineV[Uri]("http://" + containers.container.getEmulatorEndpoint).leftMap(new IllegalArgumentException(_))).flatMap(f)
    }

}
