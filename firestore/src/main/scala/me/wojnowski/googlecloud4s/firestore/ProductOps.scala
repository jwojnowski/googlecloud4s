package me.wojnowski.googlecloud4s.firestore

import scala.annotation.tailrec

object ProductOps {

  implicit class ProductNameToSnakeCase(product: Product) {
    def productPrefixSnakeCase: String =
      splitByUppercase(product.productPrefix).mkString("_")

    def productPrefixUpperSnakeCase: String =
      splitByUppercase(product.productPrefix).map(_.toUpperCase).mkString("_")

    def productPrefixLowerCamelCase: String = withFirstLetterLowercase(product.productPrefix)

    // TODO Take a closer look at the performance
    @tailrec
    private def splitByUppercase(s: String, previousChunks: Vector[String] = Vector.empty): Vector[String] = {

      val (lowercase, remaining) = withFirstLetterLowercase(s).span(_.isLower)

      if (remaining.nonEmpty)
        splitByUppercase(remaining, previousChunks :+ lowercase)
      else
        previousChunks :+ lowercase
    }

    private def withFirstLetterLowercase(s: String) =
      s.take(1).toLowerCase + s.drop(1)

  }

}
