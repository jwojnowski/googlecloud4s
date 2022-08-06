package me.wojnowski.googlecloud4s.pubsub

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.SingleContainer
import me.wojnowski.googlecloud4s.TestContainerUtils
import org.testcontainers.containers.{PubSubEmulatorContainer => JavaPubSubEmulatorContainer}

case class PubSubEmulatorContainer() extends SingleContainer[JavaPubSubEmulatorContainer] {
  override val container: JavaPubSubEmulatorContainer =
    new JavaPubSubEmulatorContainer(TestContainerUtils.dockerImage)

  def uri: String = "http://" + container.getEmulatorEndpoint

}

object PubSubEmulatorContainer {

  case class Def() extends ContainerDef {
    override type Container = PubSubEmulatorContainer

    override protected def createContainer(): PubSubEmulatorContainer = PubSubEmulatorContainer()
  }

}
