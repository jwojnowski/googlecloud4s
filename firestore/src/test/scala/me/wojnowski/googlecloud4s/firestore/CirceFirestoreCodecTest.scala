package me.wojnowski.googlecloud4s.firestore

import io.circe.Decoder
import io.circe.Encoder
import me.wojnowski.googlecloud4s.firestore.codec.FirestoreCodec
import me.wojnowski.googlecloud4s.firestore.codec.circe._
import munit.FunSuite

class CirceFirestoreCodecTest extends FunSuite {
  case class Foo(foo: String)

  implicit val encoder: Encoder[Foo] = Encoder.forProduct1[Foo, String]("foo")(_.foo)
  implicit val decoder: Decoder[Foo] = Decoder.forProduct1[Foo, String]("foo")(Foo.apply)

  val value = Value.Map(Map("foo" -> Value.String("bar")))

  test("encode") {
    val foo = Foo("bar")

    assertEquals(FirestoreCodec[Foo].encode(foo), value)
  }

  test("decode") {
    val result = FirestoreCodec[Foo].decode(value)
    val expected = Foo("bar")
    assertEquals(result, Right(expected))
  }

}
