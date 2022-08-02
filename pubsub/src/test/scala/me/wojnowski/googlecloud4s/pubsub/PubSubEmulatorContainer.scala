package me.wojnowski.googlecloud4s.pubsub

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.SingleContainer
import org.testcontainers.containers.{PubSubEmulatorContainer => JavaPubSubEmulatorContainer}
import org.testcontainers.utility.DockerImageName

case class PubSubEmulatorContainer() extends SingleContainer[JavaPubSubEmulatorContainer] {
  override val container: JavaPubSubEmulatorContainer =
    new JavaPubSubEmulatorContainer(DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:396.0.0-emulators"))

  def uri: String = "http://" + container.getEmulatorEndpoint

}

object PubSubEmulatorContainer {

  case class Def() extends ContainerDef {
    override type Container = PubSubEmulatorContainer

    override protected def createContainer(): PubSubEmulatorContainer = PubSubEmulatorContainer()
  }

}
