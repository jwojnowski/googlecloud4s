package me.wojnowski.googlecloud4s.firestore

import io.circe.Json
import munit.CatsEffectSuite
import io.circe.syntax._
import io.circe.parser._

class ValueTest extends CatsEffectSuite {
  test("Encoding boolean") {
    val result = Value.Boolean(false).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "booleanValue": false
        }
        """)

    assertEquals(result, expected)
  }

  test("Decoding boolean") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "booleanValue": false
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Boolean(false)

    assertEquals(result, Right(expected))
  }

  private def parseJson(rawJson: String): Json =
    parse(rawJson).toOption.get
}
