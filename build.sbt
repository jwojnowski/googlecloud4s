name := "googlecloud4s"

ThisBuild / isSnapshot := false

idePackagePrefix := Some("me.wojnowski.googlecloud4s")

scalacOptions += "-Ypartial-unification"

val Scala212 = "2.12.12"
val Scala213 = "2.13.5"

ThisBuild / scalaVersion := Scala213
ThisBuild / crossScalaVersions := Seq(Scala212, Scala213)
ThisBuild / organization := "me.wojnowski"

val commonSettings = Seq(
  makePom / publishArtifact := true,
  publishMavenStyle := true,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishM2Configuration := publishM2Configuration.value.withOverwrite(true)
)

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

releaseTagName := s"${version.value}"

lazy val Versions = new {
  val sttp = "3.3.5"

  val circe = "0.13.0"

  val fs2 = "3.1.1"

  val cats = new {
    val core = "2.1.0" // TODO these are a bit outdated
    val effect = "3.2.5"
  }

  val log4cats = "2.1.1"

  val fuuid = "0.8.0-M2"

  val mUnit = "0.7.29"
}

lazy val core = (project in file("core"))
  .settings(
    commonSettings ++
      Seq(
        name := "googlecloud4s-core",
        libraryDependencies += "eu.timepit" %% "refined" % "0.9.21",
        libraryDependencies += "org.typelevel" %% "cats-core" % Versions.cats.core,
        libraryDependencies += "org.typelevel" %% "cats-effect" % Versions.cats.effect,
        libraryDependencies += "org.typelevel" %% "log4cats-core" % Versions.log4cats,
        libraryDependencies += "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % Versions.sttp,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % Versions.sttp, // TODO is this required here?
        libraryDependencies += "com.softwaremill.sttp.client3" %% "circe" % Versions.sttp,
        libraryDependencies += "io.circe" %% "circe-core" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-generic" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-generic-extras" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-refined" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-literal" % Versions.circe,
        libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test
      )
  )

lazy val auth = (project in file("auth"))
  .dependsOn(core)
  .settings(
    commonSettings ++
      Seq(
        name := "googlecloud4s-auth",
        libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",
        libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "8.0.2"
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
  .dependsOn(core, auth)
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-firestore",
      libraryDependencies ++= List(
        "co.fs2" %% "fs2-core",
        "co.fs2" %% "fs2-io"
      ).map(_ % Versions.fs2)
    )
  )

lazy val logging = (project in file("logging-logback-circe"))
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-logging-logback-circe",
      libraryDependencies ++= List(
        "ch.qos.logback" % "logback-classic" % "1.2.5",
        "io.circe" %% "circe-core" % Versions.circe
      )
    )
  )

lazy val pubsub = (project in file("pubsub"))
  .dependsOn(core, auth)
  .settings(
    commonSettings ++ Seq(
      name := "googlecloud4s-pubsub",
      libraryDependencies ++= List(
        "io.chrisdavenport" %% "fuuid" % Versions.fuuid,
        "io.chrisdavenport" %% "fuuid-circe" % Versions.fuuid,
        "co.fs2" %% "fs2-core" % Versions.fs2, // TODO this is for base64 conversion, seems a bit overkill
        "org.scalameta" %% "munit" % Versions.mUnit % Test,
        "org.typelevel" %% "munit-cats-effect-3" % "1.0.0" % Test,
        "com.dimafeng" %% "testcontainers-scala-munit" % "0.39.7" % Test,
        "org.testcontainers" % "gcloud" % "1.16.0"
      ),
      Test / fork := true,
    )
  )

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full)
addCompilerPlugin("com.kubukoz" % "better-tostring" % "0.3.6" cross CrossVersion.full)

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(core, auth, storage, firestore, logging, pubsub)
