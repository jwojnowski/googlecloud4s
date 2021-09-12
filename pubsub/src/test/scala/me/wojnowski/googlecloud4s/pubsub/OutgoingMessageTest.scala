package me.wojnowski.googlecloud4s.pubsub

import io.circe.JsonObject
import munit.FunSuite
import io.circe.syntax._

class OutgoingMessageTest extends FunSuite {
  test("encoding") {
    val message = OutgoingMessage("data\n")

    val json = message.asJson
    assertEquals(
      json,
      JsonObject(
        "data" -> "ZGF0YQo=".asJson,
        "attributes" -> JsonObject.empty.asJson,
        "orderingKey" -> None.asJson
      ).asJson
    )
  }

  test("encoding with attributes and ordering key") {
    val message = OutgoingMessage("data\n", Map("key" -> "value"), Some("orderingKey"))

    val json = message.asJson
    assertEquals(
      json,
      JsonObject(
        "data" -> "ZGF0YQo=".asJson,
        "attributes" -> JsonObject("key" -> "value".asJson).asJson,
        "orderingKey" -> "orderingKey".asJson
      ).asJson
    )
  }
}
