package me.wojnowski.googlecloud4s.pubsub

import cats.effect.IO
import cats.syntax.all._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.AccessToken
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.auth.TokenProvider
import munit.CatsEffectSuite

import java.time.Instant

class PubSubTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {

  val containerDef: PubSubEmulatorContainer.Def = PubSubEmulatorContainer.Def()

  override def extractUri: PubSubEmulatorContainer => String = _.uri

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

}
