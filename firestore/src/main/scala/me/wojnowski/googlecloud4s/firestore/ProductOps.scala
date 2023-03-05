package me.wojnowski.googlecloud4s.firestore

import scala.annotation.tailrec

object ProductOps {

  implicit class ProductNameToSnakeCase(product: Product) {
    def productPrefixSnakeCase: String =
      splitByUppercase(product.productPrefix).mkString("_")

    def productPrefixUpperSnakeCase: String =
      splitByUppercase(product.productPrefix).map(_.toUpperCase).mkString("_")

    def productPrefixLowerCamelCase: String =
      withFirstLetterLowercase(product.productPrefix)

    @tailrec
    private def splitByUppercase(s: String, previousChunks: Vector[String] = Vector.empty): Vector[String] = {

      val (lowercaseLetters, remainingLetters) = withFirstLetterLowercase(s).span(_.isLower)

      if (remainingLetters.nonEmpty)
        splitByUppercase(remainingLetters, previousChunks :+ lowercaseLetters)
      else
        previousChunks :+ lowercaseLetters
    }

    private def withFirstLetterLowercase(s: String) =
      s.take(1).toLowerCase + s.drop(1)

  }

}
