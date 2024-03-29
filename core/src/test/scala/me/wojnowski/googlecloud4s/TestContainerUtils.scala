package me.wojnowski.googlecloud4s

import cats.effect.IO
import cats.effect.std.Dispatcher
import com.dimafeng.testcontainers.munit.TestContainerForAll
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Uri
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import cats.syntax.all._
import munit.Suite
import org.testcontainers.utility.DockerImageName

trait TestContainerUtils {
  this: TestContainerForAll with Suite =>

  def extractUri: containerDef.Container => String

  protected def withSttpBackend[A](f: SttpBackend[IO, Fs2Streams[IO]] => IO[A]): IO[A] =
    Dispatcher.parallel[IO].use { dispatcher =>
      HttpClientFs2Backend(dispatcher).flatMap(f)
    }

  protected def withContainerUri[A](f: String Refined Uri => IO[A]): IO[A] =
    withContainers { containers =>
      IO.fromEither(refineV[Uri](extractUri(containers)).leftMap(new IllegalArgumentException(_))).flatMap(f)
    }

}

object TestContainerUtils {
  val dockerImage: DockerImageName = DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:455.0.0-emulators")
}
