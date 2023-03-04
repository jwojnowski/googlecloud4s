package me.wojnowski.googlecloud4s.firestore

import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec
import me.wojnowski.googlecloud4s.firestore.Value
import munit.FunSuite

import java.time.Instant
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString

class FirestoreCodecTest extends FunSuite {
  val reference = Reference.Root(ProjectId("project-id")) / "collection".toCollectionId / "document".toDocumentId
  val instant = Instant.parse("2004-05-06T07:08:09.101112Z")
  val bytes: Array[Byte] = Array(13, 14)

  test("encoding") {
    val results =
      List(
        FirestoreCodec[Boolean].encode(true),
        FirestoreCodec[Int].encode(42),
        FirestoreCodec[Double].encode(1.25),
        FirestoreCodec[Instant].encode(instant),
        FirestoreCodec[String].encode("foo"),
        FirestoreCodec[Array[Byte]].encode(bytes),
        FirestoreCodec[Reference.Document].encode(reference),
        FirestoreCodec[Option[String]].encode(Some("foo")),
        FirestoreCodec[Option[String]].encode(None),
        FirestoreCodec[List[String]].encode(List("foo", "bar")),
        FirestoreCodec[Map[String, Int]].encode(Map("a" -> 1, "b" -> 2))
      )

    val expected =
      List(
        Value.Boolean(true),
        Value.Integer(42),
        Value.Double(1.25),
        Value.Timestamp(Instant.parse("2004-05-06T07:08:09.101112Z")),
        Value.String("foo"),
        Value.Bytes(bytes),
        Value.Reference(reference),
        Value.String("foo"),
        Value.Null,
        Value.Array(Value.String("foo"), Value.String("bar")),
        Value.Map(Map("a" -> Value.Integer(1), "b" -> Value.Integer(2)))
      )

    assertEquals(results, expected)
  }

  test("decoding boolean") {
    val result = FirestoreCodec[Boolean].decode(Value.Boolean(true))
    val expected = Right(true)

    assertEquals(result, expected)
  }

  test("decoding int") {
    val result = FirestoreCodec[Int].decode(Value.Integer(42))
    val expected = Right(42)
    assertEquals(result, expected)
  }

  test("decoding double") {
    val result = FirestoreCodec[Double].decode(Value.Double(1.25))
    val expected = Right(1.25)
    assertEquals(result, expected)
  }

  test("decoding timestamp") {
    val result = FirestoreCodec[Instant].decode(Value.Timestamp(instant))
    val expected = Right(instant)
    assertEquals(result, expected)
  }

  test("decoding string") {
    val result = FirestoreCodec[String].decode(Value.String("foo"))
    val expected = Right("foo")
    assertEquals(result, expected)
  }

  test("decoding bytes") {
    val result = FirestoreCodec[Array[Byte]].decode(Value.Bytes(bytes))
    val expected = Right(bytes)
    assertEquals(result, expected)
  }

  test("decoding reference") {
    val result = FirestoreCodec[Reference.Document].decode(Value.Reference(reference))
    val expected = Right(reference)
    assertEquals(result, expected)
  }

  test("decoding option (some)") {
    val result = FirestoreCodec[Option[String]].decode(Value.String("foo"))
    val expected = Right(Some("foo"))
    assertEquals(result, expected)
  }

  test("decoding option (none)") {
    val result = FirestoreCodec[Option[String]].decode(Value.Null)
    val expected = Right(None)
    assertEquals(result, expected)
  }

  test("decoding array") {
    val result = FirestoreCodec[List[String]].decode(Value.Array(Value.String("foo"), Value.String("bar")))
    val expected = Right(List("foo", "bar"))
    assertEquals(result, expected)
  }

  test("decoding map") {
    val result = FirestoreCodec[Map[String, Int]].decode(Value.Map(Map("a" -> Value.Integer(1), "b" -> Value.Integer(2))))
    val expected = Right(Map("a" -> 1, "b" -> 2))
    assertEquals(result, expected)
  }

}
