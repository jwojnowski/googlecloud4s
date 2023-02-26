package me.wojnowski.googlecloud4s.firestore

import com.dimafeng.testcontainers.ContainerDef
import com.dimafeng.testcontainers.SingleContainer
import me.wojnowski.googlecloud4s.TestContainerUtils
import org.testcontainers.containers.{FirestoreEmulatorContainer => JavaFirestoreEmulatorContainer}
import org.testcontainers.images.builder.Transferable

case class FirestoreEmulatorContainer() extends SingleContainer[JavaFirestoreEmulatorContainer] {

  private val rules =
    """
      |service cloud.firestore {
      |  match /databases/{database}/documents {
      |    match /{document=**} {
      |      allow read, write: if request.auth != null;
      |    }
      |  }
      |}
      |""".stripMargin

  override val container: JavaFirestoreEmulatorContainer =
    new JavaFirestoreEmulatorContainer(TestContainerUtils.dockerImage)
      .withCopyToContainer(Transferable.of(rules), "/firestore.rules")
//      .withCommand("/bin/sh", "-c", "firebase emulators firestore start --rules=firestore.rules --host-port 0.0.0.0:8080")
      .withCommand("/bin/sh", "-c", "gcloud emulators firestore start --host-port 0.0.0.0:8080 --rules=/firestore.rules")

  def uri: String = "http://" + container.getEmulatorEndpoint

}

object FirestoreEmulatorContainer {

  case class Def() extends ContainerDef {
    override type Container = FirestoreEmulatorContainer

    override protected def createContainer(): FirestoreEmulatorContainer = FirestoreEmulatorContainer()
  }

}
