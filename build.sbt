name := "googlecloud4s"

idePackagePrefix := Some("me.wojnowski.googlecloud4s")

scalacOptions += "-Ypartial-unification"

val Scala213 = "2.13.10"
val Scala3 = "3.2.2"

ThisBuild / scalaVersion := Scala213
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / organization := "me.wojnowski"
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/jwojnowski/googlecloud4s"), "git@github.com:jwojnowski/googlecloud4s.git"))
ThisBuild / licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer(
    id = "jwojnowski",
    name = "Jakub Wojnowski",
    email = "29680262+jwojnowski@users.noreply.github.com",
    url = url("https://github.com/jwojnowski")
  )
)
ThisBuild / versionScheme := Some("semver-spec")

import xerial.sbt.Sonatype._
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / sonatypeProjectHosting := Some(GitHubHosting("jwojnowski", "googlecloud4s", "29680262+jwojnowski@users.noreply.github.com"))

ThisBuild / homepage := Some(url("https://github.com/jwojnowski/googlecloud4s"))

val commonSettings = Seq(
  makePom / publishArtifact := true,
  publishMavenStyle := true,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishM2Configuration := publishM2Configuration.value.withOverwrite(true),
  publishTo := sonatypePublishToBundle.value
)

lazy val Versions = new {
  val sttp = "3.7.4"

  val circe = "0.14.2"

  val fs2 = "3.1.2"

  val cats = new {
    val core = "2.9.0"
    val effect = "3.4.8"
    val parse = "0.3.9"
  }

  val log4cats = "2.4.0"

  val fuuid = "0.8.0-M2"

  val mUnit = "0.7.29"
  val mUnitCatsEffect = "1.0.7"

  val testContainers = "1.17.6"
  val testContainersScalaMunit = "0.40.12"
}

lazy val core = (project in file("core"))
  .settings(
    commonSettings ++
      Seq(
        name := "googlecloud4s-core",
        libraryDependencies += "eu.timepit" %% "refined" % "0.9.27",
        libraryDependencies += "org.typelevel" %% "cats-core" % Versions.cats.core,
        libraryDependencies += "org.typelevel" %% "cats-effect" % Versions.cats.effect,
        libraryDependencies += "org.typelevel" %% "log4cats-core" % Versions.log4cats cross CrossVersion.binary,
        libraryDependencies += "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % Versions.sttp,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "fs2" % Versions.sttp,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "circe" % Versions.sttp,
        libraryDependencies += "io.circe" %% "circe-core" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-refined" % Versions.circe,
        libraryDependencies += "org.typelevel" %% "jawn-parser" % "1.4.0", // CVE-2022-21653
        libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test,
        libraryDependencies += "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testContainersScalaMunit % Test,
        libraryDependencies += "org.testcontainers" % "gcloud" % Versions.testContainers % Test,
        libraryDependencies += "org.typelevel" %% "cats-effect-testkit" % Versions.cats.effect % Test
      )
  )

lazy val auth = (project in file("auth"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    commonSettings ++
      Seq(
        name := "googlecloud4s-auth",
        libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1",
        libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "9.1.0",
        libraryDependencies += "com.github.jwt-scala" %% "jwt-circe" % "9.1.0",
        libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test,
        libraryDependencies += "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % Versions.sttp % Test
      )
  )

lazy val storage = (project in file("storage"))
  .dependsOn(core, auth)
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-storage",
      libraryDependencies ++= List(
        "co.fs2" %% "fs2-core",
        "co.fs2" %% "fs2-io"
      ).map(_ % Versions.fs2)
    )
  )

lazy val firestore = (project in file("firestore"))
  .dependsOn(core % "compile->compile;test->test", auth)
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-firestore",
      libraryDependencies ++= List(
        "co.fs2" %% "fs2-core",
        "co.fs2" %% "fs2-io"
      ).map(_ % Versions.fs2) ++ List(
        "org.scalameta" %% "munit" % Versions.mUnit % Test,
        "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
        "org.slf4j" % "slf4j-simple" % "1.7.32" % Test
      ) ++ List(
        "org.typelevel" %% "cats-parse" % Versions.cats.parse
      )
    )
  )

lazy val logging = (project in file("logging-logback-circe"))
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-logging-logback-circe",
      libraryDependencies ++= List(
        "ch.qos.logback" % "logback-classic" % "1.2.11",
        "io.circe" %% "circe-core" % Versions.circe
      )
    )
  )

lazy val pubsub = (project in file("pubsub"))
  .dependsOn(core % "compile->compile;test->test", auth)
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-pubsub",
      libraryDependencies ++= List(
        "org.scalameta" %% "munit" % Versions.mUnit % Test,
        "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test
      ),
      Test / fork := true
    )
  )

ThisBuild / libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq()
    case _            => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
  }
}

addCompilerPlugin("org.polyvariant" % "better-tostring" % "0.3.17" cross CrossVersion.full)

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(core, auth, storage, firestore, logging, pubsub)
