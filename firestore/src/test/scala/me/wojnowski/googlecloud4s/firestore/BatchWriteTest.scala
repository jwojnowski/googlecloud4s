package me.wojnowski.googlecloud4s.firestore

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.IO
import cats.syntax.all._
import me.wojnowski.googlecloud4s.firestore.Firestore.FirestoreDocument.Fields
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString
import munit.CatsEffectSuite

import java.time.Instant
import scala.concurrent.duration.DurationInt

class BatchWriteTest extends CatsEffectSuite with FirestoreTestContainer {

  val collectionA = Reference.Root(projectId).collection("collection-a".toCollectionId)
  val collectionB = Reference.Root(projectId).collection("collection-b".toCollectionId)

  val document = Fields(
    "thisIsAString" -> Value.String("FOO"),
    "anInteger" -> Value.Integer(1),
    "anArrayOfStrings" -> Value.Array(List("B", "D", "G").map(Value.String.apply)),
    "timestamp" -> Value.Timestamp(Instant.EPOCH)
  )

  val documentAA = collectionA / "document-aa".toDocumentId
  val documentAB = collectionA / "document-ab".toDocumentId
  val documentAC = collectionA / "document-ac".toDocumentId
  val documentAD = collectionA / "document-ad".toDocumentId
  val documentAE = collectionA / "document-ae".toDocumentId
  val documentAF = collectionA / "document-af".toDocumentId
  val documentAG = collectionA / "document-ag".toDocumentId
  val documentAH = collectionA / "document-ah".toDocumentId
  val documentBA = collectionB / "document-ba".toDocumentId
  val documentBB = collectionB / "document-bb".toDocumentId

  test("delete operations") {
    withFirestore { firestore =>
      for {
        _               <- firestore.set(documentAA, document)
        _               <- firestore.set(documentAB, document)
        _               <- firestore.set(documentAC, document)
        _               <- firestore.set(documentBA, document)
        _               <- firestore.set(documentBB, document)
        _               <- firestore.batchWrite(NonEmptyList.of(Write.Delete(documentAB), Write.Delete(documentBA)))
        _               <- Async[IO].sleep(1.seconds)
        collectionADocs <- firestore.streamLogFailures[Fields](collectionA).compile.toList
        collectionBDocs <- firestore.streamLogFailures[Fields](collectionB).compile.toList
      } yield {
        assert(collectionADocs.map(_._1).containsSlice(List(documentAA, documentAC)))
        assert(collectionBDocs.map(_._1).containsSlice(List(documentBB)))
      }
    }
  }

  test("transform operations") {
    withFirestore { firestore =>
      for {
        _        <- firestore.set(documentAA, document)
        _        <- firestore.set(documentAB, document)
        _        <- firestore.set(documentAC, document)
        _        <- firestore.set(documentAD, document)
        _        <- firestore.set(documentAE, document)
        _        <- firestore.set(documentAF, document)
        _        <- firestore.set(documentAG, document)
        _        <- Async[IO].sleep(1.seconds)
        response <- firestore.batchWrite(
                      NonEmptyList.of(
                        Write.DocumentTransform(
                          documentAA,
                          fieldTransforms = NonEmptyList.of(FieldTransform.Maximum(FieldPath("anInteger"), 3))
                        ),
                        Write.DocumentTransform(
                          documentAB,
                          fieldTransforms = NonEmptyList.of(FieldTransform.Maximum(FieldPath("anInteger"), -3))
                        ),
                        Write.DocumentTransform(
                          documentAC,
                          fieldTransforms = NonEmptyList.of(FieldTransform.Minimum(FieldPath("anInteger"), 3))
                        ),
                        Write.DocumentTransform(
                          documentAD,
                          fieldTransforms = NonEmptyList.of(FieldTransform.Minimum(FieldPath("anInteger"), -3))
                        ),
                        Write.DocumentTransform(
                          documentAE,
                          fieldTransforms = NonEmptyList.of(FieldTransform.Increment(FieldPath("anInteger"), 5))
                        ),
                        Write.DocumentTransform(
                          documentAF,
                          fieldTransforms = NonEmptyList.of(
                            FieldTransform.AppendMissingElements(FieldPath("anArrayOfStrings"), NonEmptyList.of("A", "B", "C", "D"))
                          )
                        ),
                        Write.DocumentTransform(
                          documentAG,
                          fieldTransforms =
                            NonEmptyList.of(FieldTransform.RemoveAllFromArray(FieldPath("anArrayOfStrings"), NonEmptyList.of("B", "G")))
                        ),
                        Write.DocumentTransform(
                          documentAH,
                          fieldTransforms =
                            NonEmptyList.of(FieldTransform.SetToServerValue(FieldPath("timestamp"), ServerValue.RequestTime))
                        )
                      )
                    )
        _        <- Async[IO].sleep(1.seconds)
        now      <- Async[IO].realTimeInstant
        valueAF  <- firestore.get[Fields](documentAF)
        valueAG  <- firestore.get[Fields](documentAG)
      } yield {
        assertEquals(
          response.writeResults.take(7).map(_.transformResults),
          List(
            Some(NonEmptyList.of(Value.Integer(3))),
            Some(NonEmptyList.of(Value.Integer(1))),
            Some(NonEmptyList.of(Value.Integer(1))),
            Some(NonEmptyList.of(Value.Integer(-3))),
            Some(NonEmptyList.of(Value.Integer(6))),
            Some(NonEmptyList.of(Value.Null)),
            Some(NonEmptyList.of(Value.Null))
          )
        )

        assertEquals(
          valueAF.map(_.toMapValue).flatMap(_.apply("anArrayOfStrings")).flatMap(_.as[List[String]].toOption),
          Some(List("B", "D", "G", "A", "C"))
        )
        assertEquals(
          valueAG.map(_.toMapValue).flatMap(_.apply("anArrayOfStrings")).flatMap(_.as[List[String]].toOption),
          Some(List("D"))
        )
        assert(
          (response
            .writeResults
            .get(7)
            .flatMap(_.transformResults.flatMap(_.head.as[Instant].toOption))
            .get
            .getEpochSecond - now.getEpochSecond) < 10
        )
      }

    }
  }

  test("update operations without a mask") {
    val newValues = Fields(
      "foo" -> Value.String("Foo"),
      "object" -> Value.Map(
        "a" -> Value.Array(
          Value.Integer(1),
          Value.Integer(2)
        )
      )
    )

    withFirestore { firestore =>
      for {
        _        <- firestore.set(documentAA, document)
        response <- firestore.batchWrite(
                      NonEmptyList.of(
                        Write.Update(
                          documentAA,
                          newValues
                        )
                      )
                    )
        _        <- Async[IO].sleep(1.second)
        fields   <- firestore.get[Fields](documentAA)
      } yield {
        assert(response.writeResults.head.updateTime.isDefined)
        assertEquals(fields, Some(newValues))
      }
    }
  }

  test("update operations with a mask") {
    val newValues = Fields(
      "thisIsAString" -> Value.String("This should replace the old value"),
      "thisIsANewMap" -> Value.Map(
        "a" -> Value.Array(
          Value.Integer(1),
          Value.Integer(2)
        )
      )
    )

    val expected = Fields(
      document.value ++ newValues.value
    )

    withFirestore { firestore =>
      for {
        _        <- firestore.set(documentAA, document)
        response <- firestore.batchWrite(
                      NonEmptyList.of(
                        Write.Update(
                          documentAA,
                          newValues,
                          updateMask = Some(DocumentMask(FieldPath("thisIsAString"), FieldPath("thisIsANewMap")))
                        )
                      )
                    )
        _        <- Async[IO].sleep(1.second)
        fields   <- firestore.get[Fields](documentAA)
      } yield {
        assert(response.writeResults.head.updateTime.isDefined)
        assertEquals(fields, Some(expected))
      }
    }
  }

  test("update with transforms") {
    val originalValues = Fields(
      "aString" -> Value.String("Initial value"),
      "anInteger" -> Value.Integer(1)
    )

    val newValues = Fields(
      "aString" -> Value.String("Changed value"),
      "anInteger" -> Value.Integer(5)
    )

    val expectedValues = Fields(
      "aString" -> Value.String("Changed value"),
      "anInteger" -> Value.Integer(2)
    )

    withFirestore { firestore =>
      for {
        _        <- firestore.set(documentAA, originalValues)
        response <- firestore.batchWrite(
                      NonEmptyList.of(
                        Write.Update(
                          documentAA,
                          newValues,
                          updateTransforms = List(FieldTransform.Increment(FieldPath("anInteger"), -3))
                        )
                      )
                    )
        _        <- Async[IO].sleep(1.second)
        fields   <- firestore.get[Fields](documentAA)
      } yield {
        assert(response.writeResults.head.updateTime.isDefined)
        assertEquals(
          response.writeResults.map(_.transformResults),
          NonEmptyList.of(
            Some(NonEmptyList.of(Value.Integer(2)))
          )
        )
        assertEquals(fields, Some(expectedValues))
      }
    }
  }

  test("update with precondition") {
    withFirestore { firestore =>
      val newValues = Fields(
        "foo" -> Value.String("bar")
      )

      for {
        _        <- firestore.set(documentAA, document)
        _        <- firestore.set(documentAB, document)
        response <- firestore.batchWrite(
                      NonEmptyList.of(
                        Write.Update(
                          documentAA,
                          newValues
                        ),
                        Write.Update(
                          documentAB,
                          newValues,
                          currentDocument = Some(Precondition.UpdateTime(Instant.EPOCH))
                        )
                      )
                    )
        _        <- Async[IO].sleep(1.second)
      } yield assertEquals(
        response.status.map(_.code),
        NonEmptyList.of(
          None,
          Some(9)
        )
      )
    }
  }

}
