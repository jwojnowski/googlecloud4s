package me.wojnowski.googlecloud4s.auth

import cats.effect.IO
import munit.CatsEffectSuite
import sttp.client3.UriContext
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.testing.SttpBackendStub

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import cats.syntax.all._
import io.circe.JsonObject
import me.wojnowski.googlecloud4s.auth.PublicKeyProvider.KeyId
import io.circe.syntax._

class PublicKeyProviderTest extends CatsEffectSuite {

  private val keyFactory = KeyFactory.getInstance("RSA")
  private val base64Decoder = Base64.getDecoder

  val nonExistentKeyId = "3b2748385828c5e4284a69b52b387c3bd46cd2a9"

  val keyId1: KeyId = "402f305b70581329ff289b5b3a67283806eca893"
  val keyId2: KeyId = "1727b6b49402b9cf95be4e8fd38aa7e7c11644b1"

  val keys: Map[KeyId, PublicKey] =
    Map(
      keyId1 -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjN4xvvGtTeXxq5DZxQxBdafPZAfXn6uowE1VsVXRaSo28GAizL0OdErMui028K3pLN1XkThebJruh7SSadG3H7WJfpxf4wyCgj1ofbRIhbjjKcPqO86Lo/Uekzsv5MeW4Q2ZOvZiJkLnp3zFnFKaeBV0P408k2HbGnHS6LEcDqDWA7G+TmE+TZIoB6HZ0Q7dN3oFYJ831NZj3IyNRC9lzNaG+S00AEvKNO+3J59qig09Z/M9yuHlU1WI+BNO8wyx+5kZFe/px6m7QQ95y9v9EZWeIKMCQkomkXYhLOa7GQT9ITh5uINeRqh4rIzY1z5uAHDkgIqHn1Ztpw1O47jOewIDAQAB",
      keyId2 -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyJdNun/DT8/krjOUFMk4UPb7KgOyoN2EIHVL77LFLUlzFwOLon1pEceYcWffNQnjdtzDCN5+q6DxlIiJyDgQhPPMpJzMcpZceo0tKd+Ve1RLEUVcbnbjyZ+inrxVWfYTOuWTsutt7EylFDIMfw1Dh14IccFG5loyLdtZX2yejhXmJzMCxTISE/lCxCIiIqu5filfc3AnnyNb66Mv/oyK5z22pc9f+dFAmT3e5IXA+0UkrEVtLl7lRGmWdBkAkEWzhh17aQ0BynxpcTX5efGyr2b5ktUObCNdKMwNE4/Berz4l7/Oz6+gWDlyjbROrHKx0B27SFHdtNHbYARJsfVsjwIDAQAB"
    )
      .fmap(encodedString => new X509EncodedKeySpec(base64Decoder.decode(encodedString)))
      .fmap(keyFactory.generatePublic)

  private val certs =
    Map(
      keyId1 -> "-----BEGIN CERTIFICATE-----\nMIIDJzCCAg+gAwIBAgIJAP8DnpyTyzWzMA0GCSqGSIb3DQEBBQUAMDYxNDAyBgNV\nBAMMK2ZlZGVyYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20w\nHhcNMjIwODE5MTUyMjA4WhcNMjIwOTA1MDMzNzA4WjA2MTQwMgYDVQQDDCtmZWRl\ncmF0ZWQtc2lnbm9uLnN5c3RlbS5nc2VydmljZWFjY291bnQuY29tMIIBIjANBgkq\nhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjN4xvvGtTeXxq5DZxQxBdafPZAfXn6uo\nwE1VsVXRaSo28GAizL0OdErMui028K3pLN1XkThebJruh7SSadG3H7WJfpxf4wyC\ngj1ofbRIhbjjKcPqO86Lo/Uekzsv5MeW4Q2ZOvZiJkLnp3zFnFKaeBV0P408k2Hb\nGnHS6LEcDqDWA7G+TmE+TZIoB6HZ0Q7dN3oFYJ831NZj3IyNRC9lzNaG+S00AEvK\nNO+3J59qig09Z/M9yuHlU1WI+BNO8wyx+5kZFe/px6m7QQ95y9v9EZWeIKMCQkom\nkXYhLOa7GQT9ITh5uINeRqh4rIzY1z5uAHDkgIqHn1Ztpw1O47jOewIDAQABozgw\nNjAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAWBgNVHSUBAf8EDDAKBggr\nBgEFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEAVMtPJ/ROml4hMswWw/y2/Dwz866Z\nGuSEQnL3MY7t7xjoFtQfqf7lKFYHNQU6lLlCdqUPiSGgBpyf1IqlwOQirToVTcda\nzzrORmDMCjzes8ZBkIpCgfpMIy/HjWvGUid98CpLp0pj5mrrMc9rpl4fj9UAU9rD\niiEgORtLuGIcKYxRGZUjvfnJqnpNDViFfXOnERI97E1Eapa6KwDAu+S4WnLfjXLJ\nnyX99EMPbBm/LcS5FxHreUarbkEMaejO6HIQ6ujq80FICV8WURKlGJ/g3L4lOd1s\nnHiJllZcma99IEJyKA9+NqfVKlCs/sQX2IdC1SXPHVTPPrqPZR1OS9Bz9w==\n-----END CERTIFICATE-----\n",
      keyId2 -> "-----BEGIN CERTIFICATE-----\nMIIDJzCCAg+gAwIBAgIJALvVGVJGRFGzMA0GCSqGSIb3DQEBBQUAMDYxNDAyBgNV\nBAMMK2ZlZGVyYXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20w\nHhcNMjIwODExMTUyMjA3WhcNMjIwODI4MDMzNzA3WjA2MTQwMgYDVQQDDCtmZWRl\ncmF0ZWQtc2lnbm9uLnN5c3RlbS5nc2VydmljZWFjY291bnQuY29tMIIBIjANBgkq\nhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyJdNun/DT8/krjOUFMk4UPb7KgOyoN2E\nIHVL77LFLUlzFwOLon1pEceYcWffNQnjdtzDCN5+q6DxlIiJyDgQhPPMpJzMcpZc\neo0tKd+Ve1RLEUVcbnbjyZ+inrxVWfYTOuWTsutt7EylFDIMfw1Dh14IccFG5loy\nLdtZX2yejhXmJzMCxTISE/lCxCIiIqu5filfc3AnnyNb66Mv/oyK5z22pc9f+dFA\nmT3e5IXA+0UkrEVtLl7lRGmWdBkAkEWzhh17aQ0BynxpcTX5efGyr2b5ktUObCNd\nKMwNE4/Berz4l7/Oz6+gWDlyjbROrHKx0B27SFHdtNHbYARJsfVsjwIDAQABozgw\nNjAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAWBgNVHSUBAf8EDDAKBggr\nBgEFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEAKyBoGXvSVfeik/w8IHtRzoVyo7//\nGcHc+bK21KAMWIuqBdVCa/0ESUKPhH2oi8vaIYUvnSIr4e4j9fJFBxKFeqNRqw5X\nn5Kr4qSqPtg8dhkif5ZpMq3C5BnDZaBUM51M/n9DCY6PUQAq+cCQz+c+OJgvby8x\nWmhfE628eF4qkk1jpdfpR9lSIHyyo5HEUmwRHAWAitLh2fgMD3v8T3YnjhqEYidH\n99wntx4HEmmWHA9S9GQ5XsZrR78GTcSRbLbQ+RVII8X1KdTCQBdSBnpm24ERkhYn\nsfGzb5vtIj+tG2e+L4TY/IA4MErqxC8RWNpWo+dKYfWivAb8QU7qwvXYzw==\n-----END CERTIFICATE-----\n"
    )

  val trueGoogleJwksResponse =
    """{
      |  "keys": [
      |    {
      |      "kty": "RSA",
      |      "use": "sig",
      |      "kid": "1727b6b49402b9cf95be4e8fd38aa7e7c11644b1",
      |      "e": "AQAB",
      |      "alg": "RS256",
      |      "n": "yJdNun_DT8_krjOUFMk4UPb7KgOyoN2EIHVL77LFLUlzFwOLon1pEceYcWffNQnjdtzDCN5-q6DxlIiJyDgQhPPMpJzMcpZceo0tKd-Ve1RLEUVcbnbjyZ-inrxVWfYTOuWTsutt7EylFDIMfw1Dh14IccFG5loyLdtZX2yejhXmJzMCxTISE_lCxCIiIqu5filfc3AnnyNb66Mv_oyK5z22pc9f-dFAmT3e5IXA-0UkrEVtLl7lRGmWdBkAkEWzhh17aQ0BynxpcTX5efGyr2b5ktUObCNdKMwNE4_Berz4l7_Oz6-gWDlyjbROrHKx0B27SFHdtNHbYARJsfVsjw"
      |    },
      |    {
      |      "kid": "402f305b70581329ff289b5b3a67283806eca893",
      |      "e": "AQAB",
      |      "alg": "RS256",
      |      "use": "sig",
      |      "kty": "RSA",
      |      "n": "jN4xvvGtTeXxq5DZxQxBdafPZAfXn6uowE1VsVXRaSo28GAizL0OdErMui028K3pLN1XkThebJruh7SSadG3H7WJfpxf4wyCgj1ofbRIhbjjKcPqO86Lo_Uekzsv5MeW4Q2ZOvZiJkLnp3zFnFKaeBV0P408k2HbGnHS6LEcDqDWA7G-TmE-TZIoB6HZ0Q7dN3oFYJ831NZj3IyNRC9lzNaG-S00AEvKNO-3J59qig09Z_M9yuHlU1WI-BNO8wyx-5kZFe_px6m7QQ95y9v9EZWeIKMCQkomkXYhLOa7GQT9ITh5uINeRqh4rIzY1z5uAHDkgIqHn1Ztpw1O47jOew"
      |    }
      |  ]
      |}""".stripMargin

  test("Google v1 provider") {
    val trueGoogleCertsV1 =
      JsonObject(
        keyId1 -> certs(keyId1).asJson,
        keyId2 -> certs(keyId2).asJson
      )

    implicit val backend: SttpBackendStub[IO, Any] =
      SttpBackendStub
        .apply[IO, Any](new CatsMonadAsyncError)
        .whenRequestMatches { request =>
          request.uri == uri"https://www.googleapis.com/oauth2/v1/certs" && request.method == sttp.model.Method.GET
        }
        .thenRespond(trueGoogleCertsV1.asJson.noSpaces)

    val keyProvider = PublicKeyProvider.googleV1[IO]()

    for {
      key1           <- keyProvider.getKey(keyId1)
      key2           <- keyProvider.getKey(keyId2)
      nonExistentKey <- keyProvider.getKey(nonExistentKeyId)
      all            <- keyProvider.getAllKeys
    } yield {
      assertEquals(key1, Right(keys.apply(keyId1)))
      assertEquals(key2, Right(keys.apply(keyId2)))
      assertEquals(nonExistentKey, Left(PublicKeyProvider.Error.CouldNotFindPublicKey(nonExistentKeyId)))
      assertEquals(
        all,
        Right(
          Map(
            keyId1 -> Right(keys.apply(keyId1)),
            keyId2 -> Right(keys.apply(keyId2))
          )
        )
      )
    }

  }

  test("Google v3/jwk provider") {
    implicit val backend: SttpBackendStub[IO, Any] =
      SttpBackendStub
        .apply[IO, Any](new CatsMonadAsyncError)
        .whenRequestMatches { request =>
          request.uri == uri"https://www.googleapis.com/oauth2/v3/certs" && request.method == sttp.model.Method.GET
        }
        .thenRespond(trueGoogleJwksResponse)

    val keyProvider = PublicKeyProvider.jwk[IO]()

    for {
      key1           <- keyProvider.getKey(keyId1)
      key2           <- keyProvider.getKey(keyId2)
      nonExistentKey <- keyProvider.getKey(nonExistentKeyId)
      all            <- keyProvider.getAllKeys
    } yield {
      assertEquals(key1, Right(keys.apply(keyId1)))
      assertEquals(key2, Right(keys.apply(keyId2)))
      assertEquals(nonExistentKey, Left(PublicKeyProvider.Error.CouldNotFindPublicKey(nonExistentKeyId)))
      assertEquals(
        all,
        Right(
          Map(
            keyId1 -> Right(keys.apply(keyId1)),
            keyId2 -> Right(keys.apply(keyId2))
          )
        )
      )
    }
  }

  test("Cached provider returns the key without a new call") {
    // TODO
  }

  test("Cached provider always gets a fresh response for getAll") {
    // TODO
  }

  test("Cached provider refreshes the cache when a new key is requested") {
    // TODO
  }
}
