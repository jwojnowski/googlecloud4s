package me.wojnowski.googlecloud4s.firestore

import munit.FunSuite

// https://cloud.google.com/firestore/quotas#collections_documents_and_fields
class DocumentIdTest extends FunSuite {
  test("ID longer than 1500 bytes is invalid") {
    val result = DocumentId.parse(List.fill(1501)('a').mkString)
    assert(result.isLeft)
  }

  test("Empty name is invalid") {
    val result = DocumentId.parse("")
    assert(result.isLeft)
  }

  test("Any UTF-8 character is valid") {
    val result = DocumentId.parse("🤔🎉?")
    assertEquals(result.map(_.value), Right("🤔🎉?"))
  }

  test("ID with forward slash is invalid") {
    val result = DocumentId.parse("foo/bar")
    assert(result.isLeft)
  }

  test("[.] is invalid") {
    val result = DocumentId.parse(".")
    assert(result.isLeft)
  }

  test("[..] is invalid") {
    val result = DocumentId.parse("..")
    assert(result.isLeft)
  }

  test("Dots with other characters are valid") {
    val result = DocumentId.parse(".foo.")
    assertEquals(result.map(_.value), Right(".foo."))
  }

  test("Name starting and ending with __ is invalid") {
    val result = DocumentId.parse("__foo__")
    assert(result.isLeft)
  }

  test("Name starting with __ is valid") {
    val result = DocumentId.parse("__foo")
    assertEquals(result.map(_.value), Right("__foo"))
  }

  test("Name ending with __ is valid") {
    val result = DocumentId.parse("foo__")
    assertEquals(result.map(_.value), Right("foo__"))
  }

  test("Name containing double underscores twice is valid") {
    val result = DocumentId.parse("asdf__foo__asdf")
    assertEquals(result.map(_.value), Right("asdf__foo__asdf"))
  }

}
