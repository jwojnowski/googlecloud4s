package me.wojnowski.googlecloud4s.pubsub

import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import sttp.client3.SttpBackend
import cats.syntax.all._
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.auth.Scopes
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.pubsub.PubSub.Error.UnexpectedResponse
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.model.StatusCode
import sttp.model.Uri

import scala.util.control.NoStackTrace

trait PubSub[F[_]] {
  def publish(topic: Topic, message: OutgoingMessage): F[Unit] = publish(topic, NonEmptyList.one(message))
  def publish(topic: Topic, messages: NonEmptyList[OutgoingMessage]): F[Unit]

  def createTopic(topic: Topic): F[Unit]
}

object PubSub {

  def apply[F[_]](implicit ev: PubSub[F]): PubSub[F] = ev

  def instance[F[_]: Sync](
    projectId: ProjectId,
    backend: SttpBackend[F, Any],
    uriOverride: Option[String Refined refined.string.Uri] = none
  )(
    implicit tokenProvider: TokenProvider[F]
  ): PubSub[F] =
    new PubSub[F] {
      import sttp.client3._
      import sttp.client3.circe._
      import io.circe.literal._

      implicit val logger: Logger[F] = Slf4jLogger.getLogger[F]

      val baseUri: Uri = uriOverride.fold(uri"https://pubsub.googleapis.com")(u => uri"$u")
      val scope = Scopes("https://www.googleapis.com/auth/pubsub")

      override def createTopic(topic: Topic): F[Unit] = {
        for {
          _        <- Logger[F].debug(s"Creating topic [${topic.name}]...")
          token    <- tokenProvider.getAccessToken(scope)
          response <- backend
                        .send(
                          basicRequest
                            .header("Authorization", s"Bearer ${token.value}")
                            .put(uri"$baseUri/v1/projects/${projectId.value}/topics/${topic.name}")
                        )
          _        <- Sync[F].whenA(!response.code.isSuccess)(UnexpectedResponse(response.show()).raiseError[F, Unit])
          _        <- Logger[F].debug(s"Created topic [${topic.name}].")
        } yield ()
      }.onError {
        case t => Logger[F].error(t)(s"Failed to create topic [${topic.name}] due to: $t")
      }

      override def publish(topic: Topic, messages: NonEmptyList[OutgoingMessage]): F[Unit] = {
        for {
          _        <- Logger[F].debug(s"Publishing [${messages.size}] message(s) to topic [${topic.name}]...")
          token    <- tokenProvider.getAccessToken(scope)
          response <- backend
                        .send(
                          basicRequest
                            .post(uri"$baseUri/v1/projects/${projectId.value}/topics/${topic.name}:publish")
                            .header("Authorization", s"Bearer ${token.value}")
                            .body(json"""{
                           "messages": $messages
                         }""")
                        )
          _        <- response.code match {
                        case code if code.isSuccess => ().pure[F]
                        case StatusCode.NotFound    => Error.TopicNotFound.raiseError[F, Unit]
                        case _                      => UnexpectedResponse(response.show()).raiseError[F, Unit]
                      }
          _        <- Logger[F].info(s"Published [${messages.size}] message(s) to topic [${topic.name}].")
        } yield ()
      }.onError {
        case t => logger.error(t)(s"Failed to publish [${messages.size}] message(s) to topic [${topic.name}] due to: $t")
      }

    }

  sealed trait Error extends NoStackTrace with Product with Serializable

  object Error {
    case object TopicNotFound extends Error
    case class UnexpectedResponse(details: String) extends Error
  }

}
