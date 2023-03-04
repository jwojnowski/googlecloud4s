package me.wojnowski.googlecloud4s.firestore

import cats.effect.IO
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import cats.syntax.all._
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.EncoderOps
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.TestContainerUtils
import me.wojnowski.googlecloud4s.auth.TokenProvider
import me.wojnowski.googlecloud4s.firestore.Firestore.FieldFilter
import me.wojnowski.googlecloud4s.firestore.Firestore.Order
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString

import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

// TODO add a set of meaningful tests
class StreamingTest extends CatsEffectSuite with TestContainerForAll with TestContainerUtils {

  override def extractUri: FirestoreEmulatorContainer => String = _.uri

  override def munitTimeout: Duration = 1.minute

  val containerDef: FirestoreEmulatorContainer.Def = FirestoreEmulatorContainer.Def()

  val projectId: ProjectId = ProjectId("project-id")

  import me.wojnowski.googlecloud4s.firestore.codec.circe._

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  test("Cursors with orderBy parameters") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        val collection = Reference.Root(projectId).collection("test-collection".toCollectionId)

        val fifteens = List.fill(10)(JsonObject("foo" -> "15".asJson).asJson)
        val sixteens = List.fill(10)(JsonObject("foo" -> "16".asJson).asJson)
        val items = fifteens ++ sixteens

        for {
          _       <- items.traverse(Firestore[IO].add[Json](collection, _))
          results <- Firestore[IO]
                       .stream[Json](
                         collection,
                         filters = List(
                           FieldFilter("foo", FieldFilter.Operator.>, "14"),
                           FieldFilter("foo", FieldFilter.Operator.<, "16")
                         ),
                         orderBy = List(
                           Order("foo", Order.Direction.Descending)
                         ),
                         pageSize = 3
                       )
                       .compile
                       .toList
        } yield assertEquals(results.flatMap(_._2.toOption), fifteens)

      }
    }
  }

  test("Subcollections") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val rootCollection = Reference.Root(projectId).collection("collection-a".toCollectionId)
        val subCollectionId = "collection-b".toCollectionId
        val documentAPath = rootCollection.document("document-a".toDocumentId)
        val documentBPath = rootCollection.document("document-b".toDocumentId)

        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        val rootA = JsonObject("foo" -> "rootA".asJson)
        val rootB = JsonObject("foo" -> "rootB".asJson)
        val documentAA = JsonObject("bar" -> "AA".asJson)
        val documentAB = JsonObject("bar" -> "AB".asJson)
        val documentBA = JsonObject("bar" -> "BA".asJson)
        val documentBB = JsonObject("bar" -> "BB".asJson)

        for {
          _                <- Firestore[IO].set(documentAPath, rootA)
          _                <- Firestore[IO].set(documentBPath, rootB)
          _                <- Firestore[IO].add(documentAPath.collection(subCollectionId), documentAA)
          _                <- Firestore[IO].add(documentAPath.collection(subCollectionId), documentAB)
          _                <- Firestore[IO].add(documentBPath.collection(subCollectionId), documentBA)
          _                <- Firestore[IO].add(documentBPath.collection(subCollectionId), documentBB)
          rootItems        <- Firestore[IO].stream[JsonObject](rootCollection).compile.toList
          collectionAItems <- Firestore[IO].stream[JsonObject](documentAPath.collection(subCollectionId)).compile.toList
          collectionBItems <- Firestore[IO].stream[JsonObject](documentBPath.collection(subCollectionId)).compile.toList
        } yield {
          assertEquals(rootItems.collect { case (_, Right(jsonObject)) => jsonObject }.toSet, Set(rootA, rootB))
          assertEquals(rootItems.collect { case (_, Right(jsonObject)) => jsonObject }.toSet, Set(rootA, rootB))
          assertEquals(collectionAItems.collect { case (_, Right(jsonObject)) => jsonObject }.toSet, Set(documentAA, documentAB))
          assertEquals(collectionBItems.collect { case (_, Right(jsonObject)) => jsonObject }.toSet, Set(documentBA, documentBB))
        }
      }
    }
  }

  test("Streaming documents from non-existent reference") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        val rootCollectionId = "collection-x".toCollectionId
        val subCollectionId = "collection-y".toCollectionId
        val documentAPath = Reference.Root(projectId).collection(rootCollectionId).document("document-a".toDocumentId)

        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        for {
          items <- Firestore[IO].stream[JsonObject](documentAPath.collection(subCollectionId)).compile.toList
        } yield assertEquals(items, List.empty)
      }
    }
  }

}
