package me.wojnowski.googlecloud4s.firestore

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.SingleContainer
import org.testcontainers.containers.{FirestoreEmulatorContainer => JavaFirestoreEmulatorContainer}
import org.testcontainers.utility.DockerImageName

case class FirestoreEmulatorContainer() extends SingleContainer[JavaFirestoreEmulatorContainer] {
  override val container: JavaFirestoreEmulatorContainer =
    new JavaFirestoreEmulatorContainer(DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:396.0.0-emulators"))

  def uri: String = "http://" + container.getEmulatorEndpoint

}

object FirestoreEmulatorContainer {

  case class Def() extends ContainerDef {
    override type Container = FirestoreEmulatorContainer

    override protected def createContainer(): FirestoreEmulatorContainer = FirestoreEmulatorContainer()
  }

}
