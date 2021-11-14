name := "googlecloud4s"

ThisBuild / isSnapshot := false

idePackagePrefix := Some("me.wojnowski.googlecloud4s")

scalacOptions += "-Ypartial-unification"

val Scala213 = "2.13.7"
val Scala3 = "3.1.0"

ThisBuild / scalaVersion := Scala3
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
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
  val sttp = "3.3.16"

  val circe = "0.14.1"

  val fs2 = "3.1.2"

  val cats = new {
    val core = "2.6.1"
    val effect = "3.2.9"
  }

  val log4cats = "2.1.1"

  val fuuid = "0.8.0-M2"

  val mUnit = "0.7.29"
  val mUnitCatsEffect = "1.0.6"

  val testContainers = "1.16.2"
  val testContainersScalaMunit = "0.39.11"
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
        libraryDependencies += "com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % Versions.sttp, // TODO is this required here?
        libraryDependencies += "com.softwaremill.sttp.client3" %% "circe" % Versions.sttp,
        libraryDependencies += "io.circe" %% "circe-core" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-generic" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-generic-extras" % Versions.circe cross CrossVersion.for3Use2_13,
        libraryDependencies += "io.circe" %% "circe-refined" % Versions.circe,
        libraryDependencies += "io.circe" %% "circe-literal" % Versions.circe cross CrossVersion.for3Use2_13,
        libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test
      )
  )

lazy val auth = (project in file("auth"))
  .dependsOn(core)
  .settings(
    commonSettings ++
      Seq(
        name := "googlecloud4s-auth",
        libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0",
        libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "9.0.1",
        libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test,
        libraryDependencies += "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
        libraryDependencies += "org.typelevel" %% "cats-effect-kernel-testkit" % Versions.cats.effect % Test,
        libraryDependencies += "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.3.16" % Test,
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
      ).map(_ % Versions.fs2) ++ List(
        "org.scalameta" %% "munit" % Versions.mUnit % Test,
        "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
        "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testContainersScalaMunit % Test,
        "org.testcontainers" % "gcloud" % Versions.testContainers % Test,
        "org.slf4j" % "slf4j-simple" % "1.7.32" % Test
      )
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
        "org.scalameta" %% "munit" % Versions.mUnit % Test,
        "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
        "com.dimafeng" %% "testcontainers-scala-munit" % Versions.testContainersScalaMunit % Test,
        "org.testcontainers" % "gcloud" % Versions.testContainers % Test
      ),
      Test / fork := true
    )
  )

ThisBuild / libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq()
    case _ => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
  }
}

addCompilerPlugin("org.polyvariant" % "better-tostring" % "0.3.11" cross CrossVersion.full)

val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(core, auth, storage, firestore, logging, pubsub)
