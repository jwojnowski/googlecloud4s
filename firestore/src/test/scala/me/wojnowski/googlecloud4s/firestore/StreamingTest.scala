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

  import FirestoreCodec.circe._

  implicit val tokenProvider: TokenProvider[IO] = TokenProviderMock.instance

  test("Cursors with orderBy parameters") {
    withContainerUri { uri =>
      withSttpBackend { backend =>
        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        val collection = "test-collection".toCollectionId

        val fifteens = List.fill(10)(JsonObject("foo" -> "15".asJson).asJson)
        val sixteens = List.fill(10)(JsonObject("foo" -> "16".asJson).asJson)
        val items = fifteens ++ sixteens

        for {
          _       <- items.traverse(Firestore[IO].add[Json](collection, _))
          results <- Firestore[IO]
                       .stream[Json](
                         collection,
                         filters = List(
                           FieldFilter("foo", "14", FieldFilter.Operator.>),
                           FieldFilter("foo", "16", FieldFilter.Operator.<)
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
        val rootCollectionId = "collection-a".toCollectionId
        val subCollectionId = "collection-b".toCollectionId
        val documentAPath = Reference.Document(Reference.Root(projectId), rootCollectionId, "document-a".toDocumentId)
        val documentBPath = Reference.Document(Reference.Root(projectId), rootCollectionId, "document-b".toDocumentId)

        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        val rootA = JsonObject("foo" -> "rootA".asJson)
        val rootB = JsonObject("foo" -> "rootB".asJson)
        val documentAA = JsonObject("bar" -> "AA".asJson)
        val documentAB = JsonObject("bar" -> "AB".asJson)
        val documentBA = JsonObject("bar" -> "BA".asJson)
        val documentBB = JsonObject("bar" -> "BB".asJson)

        for {
          _                <- Firestore[IO].put(documentAPath, rootA)
          _                <- Firestore[IO].put(documentBPath, rootB)
          _                <- Firestore[IO].add(subCollectionId, documentAA, parent = documentAPath)
          _                <- Firestore[IO].add(subCollectionId, documentAB, parent = documentAPath)
          _                <- Firestore[IO].add(subCollectionId, documentBA, parent = documentBPath)
          _                <- Firestore[IO].add(subCollectionId, documentBB, parent = documentBPath)
          rootItems        <- Firestore[IO].stream[JsonObject](rootCollectionId).compile.toList
          collectionAItems <- Firestore[IO].stream[JsonObject](subCollectionId, parent = documentAPath).compile.toList
          collectionBItems <- Firestore[IO].stream[JsonObject](subCollectionId, parent = documentBPath).compile.toList
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
        val documentAPath = Reference.Document(Reference.Root(projectId), rootCollectionId, "document-a".toDocumentId)

        implicit val firestore: Firestore[IO] = Firestore.instance[IO](backend, projectId, uri.some)

        for {
          items <- Firestore[IO].stream[JsonObject](subCollectionId, parent = documentAPath).compile.toList
        } yield assertEquals(items, List.empty)
      }
    }
  }

}
