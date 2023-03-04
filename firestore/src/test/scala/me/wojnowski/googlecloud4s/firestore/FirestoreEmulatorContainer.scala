package me.wojnowski.googlecloud4s.firestore

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.SingleContainer
import me.wojnowski.googlecloud4s.TestContainerUtils
import org.testcontainers.containers.{FirestoreEmulatorContainer => JavaFirestoreEmulatorContainer}

case class FirestoreEmulatorContainer() extends SingleContainer[JavaFirestoreEmulatorContainer] {

  override val container: JavaFirestoreEmulatorContainer =
    new JavaFirestoreEmulatorContainer(TestContainerUtils.dockerImage)
      .withCommand("/bin/sh", "-c", "gcloud emulators firestore start --host-port 0.0.0.0:8080")

  def uri: String = "http://" + container.getEmulatorEndpoint

}

object FirestoreEmulatorContainer {

  case class Def() extends ContainerDef {
    override type Container = FirestoreEmulatorContainer

    override protected def createContainer(): FirestoreEmulatorContainer = FirestoreEmulatorContainer()
  }

}
