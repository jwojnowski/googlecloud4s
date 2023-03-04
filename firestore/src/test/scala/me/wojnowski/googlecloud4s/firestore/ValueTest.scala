package me.wojnowski.googlecloud4s.firestore

import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import me.wojnowski.googlecloud4s.ProjectId
import me.wojnowski.googlecloud4s.firestore.Helpers.CollectionIdString
import me.wojnowski.googlecloud4s.firestore.Helpers.ShortNameString
import munit.CatsEffectSuite

import java.time.Instant

class ValueTest extends CatsEffectSuite {

  private val reference =
    Reference.Root(ProjectId("project-id")) / "collection-a".toCollectionId / "document-x".toDocumentId /
      "collection-b".toCollectionId / "document-y".toDocumentId

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

  test("Encoding null") {
    val result = Value.Null.firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "nullValue": null
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

  test("Encoding reference") {
    val result = Value.Reference(reference).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "referenceValue": "projects/project-id/databases/(default)/documents/collection-a/document-x/collection-b/document-y"
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

  test("Encoding array (empty)") {
    val result = Value.Array().firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": []
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding array (single value)") {
    val result = Value.Array(Value.String("foo")).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              }
            ]
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding array (multiple values of the same type)") {
    val result = Value.Array(Value.String("foo"), Value.String("bar")).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              },
              {
                "stringValue": "bar"
              }
            ]
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding array (multiple values of different types)") {
    val result = Value.Array(Value.String("foo"), Value.Integer(42)).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              },
              {
                "integerValue": 42
              }
            ]
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding map (empty)") {
    val result =
      Value
        .Map(Map.empty[String, Value])
        .firestoreJson
        .asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {}
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding map (multiple values of same type)") {
    val result = Value
      .Map(
        Map(
          "foo" -> Value.Integer(1),
          "bar" -> Value.Integer(2)
        )
      )
      .firestoreJson
      .asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {
              "foo": {
                "integerValue": 1
              },
              "bar": {
                "integerValue": 2
              }
            }
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding map (multiple values of different types)") {
    val result =
      Value
        .Map(
          Map(
            "foo" -> Value.Integer(1),
            "bar" -> Value.String("baz")
          )
        )
        .firestoreJson
        .asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {
              "foo": {
                "integerValue": 1
              },
              "bar": {
                "stringValue": "baz"
              }
            }
          }
        }
        """)

    assertEquals(result, expected)
  }

  test("Encoding geo point") {
    val result = Value.GeoPoint(-39.296405, 174.063464).firestoreJson.asJson
    val expected =
      // language=JSON
      parseJson("""
        {
          "geoPointValue": {
            "latitude": -39.296405,
            "longitude": 174.063464
          }
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

  test("Decoding integer") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "integerValue": 42
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Integer(42)

    assertEquals(result, Right(expected))
  }

  test("Decoding double") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "doubleValue": 1.25
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Double(1.25)

    assertEquals(result, Right(expected))
  }

  test("Decoding instant") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "timestampValue": "2001-02-03T04:05:06.070809Z"
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Timestamp(Instant.parse("2001-02-03T04:05:06.070809Z"))

    assertEquals(result, Right(expected))
  }

  test("Decoding null") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "nullValue": null
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Null

    assertEquals(result, Right(expected))
  }

  test("Decoding string") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "stringValue": "foo"
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.String("foo")

    assertEquals(result, Right(expected))
  }

  test("Decoding reference") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "referenceValue": "projects/project-id/databases/(default)/documents/collection-a/document-x/collection-b/document-y"
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Reference(reference)

    assertEquals(result, Right(expected))
  }

  test("Decoding bytes") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "bytesValue": "Zm9v"
        }
        """)

    val result = Value.fromFirestoreJson(rawJson).flatMap(_.narrowCollect { case Value.Bytes(bytes) => bytes.toList })
    val expected = Right(List[Byte](102, 111, 111))

    assertEquals(result, expected)
  }

  test("Decoding geo point") {
    val rawJson =
      // language=JSON
      parseJson("""
      {
        "geoPointValue": {
          "latitude": -39.296405,
          "longitude": 174.063464
        }
      }
      """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.GeoPoint(-39.296405, 174.063464)

    assertEquals(result, Right(expected))
  }

  test("Decoding array (empty)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": []
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Array(Seq.empty)

    assertEquals(result, Right(expected))
  }

  test("Decoding array (single value)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              }
            ]
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Array(Value.String("foo"))

    assertEquals(result, Right(expected))
  }

  test("Decoding array (multiple values, same type)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              },
              {
                "stringValue": "bar"
              }
            ]
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Array(Value.String("foo"), Value.String("bar"))

    assertEquals(result, Right(expected))
  }

  test("Decoding array (multiple values, different types)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "arrayValue": {
            "values": [
              {
                "stringValue": "foo"
              },
              {
                "integerValue": 42
              }
            ]
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Array(Value.String("foo"), Value.Integer(42))

    assertEquals(result, Right(expected))
  }

  test("Decoding map (empty)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {}
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Map(Map.empty[String, Value])

    assertEquals(result, Right(expected))
  }

  test("Decoding map (single value)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {
              "foo": {
                "stringValue": "A"
              }
            }
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Map("foo" -> Value.String("A"))

    assertEquals(result, Right(expected))
  }

  test("Decoding map (multiple values, same type)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {
              "foo": {
                "stringValue": "A"
              },
              "bar": {
                "stringValue": "B"
              }
            }
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Map("foo" -> Value.String("A"), "bar" -> Value.String("B"))

    assertEquals(result, Right(expected))
  }

  test("Decoding map (multiple values, different types)") {
    val rawJson =
      // language=JSON
      parseJson("""
        {
          "mapValue": {
            "fields": {
              "foo": {
                "stringValue": "A"
              },
              "bar": {
                "integerValue": 42
              }
            }
          }
        }
        """)

    val result = Value.fromFirestoreJson(rawJson)
    val expected = Value.Map("foo" -> Value.String("A"), "bar" -> Value.Integer(42))

    assertEquals(result, Right(expected))
  }

  private def parseJson(rawJson: String): Json =
    parse(rawJson).toOption.get
}
