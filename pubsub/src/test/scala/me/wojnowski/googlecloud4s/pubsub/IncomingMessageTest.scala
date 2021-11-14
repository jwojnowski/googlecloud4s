package me.wojnowski.googlecloud4s.pubsub

import io.circe.Decoder
import munit.FunSuite
import io.circe.parser.decode
import me.wojnowski.googlecloud4s.pubsub.IncomingMessageTest.Foo
import me.wojnowski.googlecloud4s.pubsub.IncomingMessageTest.fooDecoder

import java.time.OffsetDateTime

class IncomingMessageTest extends FunSuite {
  test("decoding with attributes") {
    val rawJson = """{
           "attributes": {
             "key": "value"
           },
           "data": "ZGF0YQo=",
           "messageId": "2070443601311540",
           "message_id": "2070443601311540",
           "publishTime": "2021-02-26T19:13:55.749Z",
           "publish_time": "2021-02-26T19:13:55.749Z"
         }
       """

    val result = decode[IncomingMessage](rawJson)

    assertEquals(
      result,
      Right(
        IncomingMessage(
          "data\n".stripMargin,
          Map("key" -> "value"),
          "2070443601311540",
          OffsetDateTime.parse("2021-02-26T19:13:55.749Z")
        )
      )
    )
  }

  test("decoding without attributes") {
    val rawJson = """{
           "data": "ZGF0YQo=",
           "messageId": "2070443601311540",
           "message_id": "2070443601311540",
           "publishTime": "2021-02-26T19:13:55.749Z",
           "publish_time": "2021-02-26T19:13:55.749Z"
         }
       """

    val result = decode[IncomingMessage](rawJson)

    assertEquals(
      result,
      Right(
        IncomingMessage(
          "data\n".stripMargin,
          Map.empty,
          "2070443601311540",
          OffsetDateTime.parse("2021-02-26T19:13:55.749Z")
        )
      )
    )
  }

  test("decoding JSON payload") {
    import IncomingMessage.circe._
    val message = IncomingMessage(
      """{"bar": "BAR", "baz": 3}""",
      Map.empty,
      "2070443601311540",
      OffsetDateTime.parse("2021-02-26T19:13:55.749Z")
    )

    val result = message.dataAs[Foo]

    assertEquals(result, Right(Foo("BAR", 3)))
  }
}

object IncomingMessageTest {
  case class Foo(bar: String, baz: Int)

  implicit val fooDecoder: Decoder[Foo] = Decoder.forProduct2[Foo, String, Int]("bar", "baz")(Foo.apply)
}
