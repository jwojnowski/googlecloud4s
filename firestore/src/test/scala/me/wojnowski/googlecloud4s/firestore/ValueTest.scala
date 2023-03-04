package me.wojnowski.googlecloud4s.firestore

import io.circe.Json
import munit.CatsEffectSuite
import io.circe.syntax._
import io.circe.parser._
import me.wojnowski.googlecloud4s.firestore.Value

import java.time.Instant

// TODO add other cases
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

  test("Encoding int") {
    val result = Value.Integer(42).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "integerValue": 42
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding double") {
    val result = Value.Double(1.25).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "doubleValue": 1.25
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding instant") {
    val result = Value.Timestamp(Instant.parse("2004-05-06T07:08:09.101112Z")).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "timestampValue": "2004-05-06T07:08:09.101112Z"
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding string") {
    val result = Value.String("foo").firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "stringValue": "foo"
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding bytes") {
    val result = Value.Bytes(Array(102, 111, 111)).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "bytesValue": "Zm9v"
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
