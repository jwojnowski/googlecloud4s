package me.wojnowski.googlecloud4s.pubsub

import cats.effect.IO
import cats.effect.std.Dispatcher
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.SingleContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import munit.FunSuite
import org.testcontainers.utility.DockerImageName
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import cats.syntax.all._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineMV
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uri
import io.chrisdavenport.fuuid.FUUID
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.Scope
import me.wojnowski.googlecloud4s.auth.Token
import me.wojnowski.googlecloud4s.auth.TokenProvider

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PubSubTest extends CatsEffectSuite with TestContainerForAll {

  val containerDef = PubSubEmulatorContainer.Def()

  val topic = Topic("test-topic")
  val projectId = ProjectId("test-project")

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.const(Token("test", Scope("test"), Instant.EPOCH))

  test("Publishing to non-existent topic results in an error") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val pubSub = PubSub.instance(projectId, backend, uriOverride = uri.some)
        val message =
          Message(
            "eyJ0ZXN0IjogImZvbyJ9Cg==",
            FUUID.fuuid("c636fe03-1fcf-4ccf-9c41-c29542c262d3"),
            Instant.EPOCH.atOffset(ZoneOffset.UTC)
          )

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
        val message = Message("data", FUUID.fuuid("c636fe03-1fcf-4ccf-9c41-c29542c262d3"), OffsetDateTime.now()) // TODO

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
