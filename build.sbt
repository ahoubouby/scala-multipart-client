name := "scala-multipart-client"

organization := "com.multipart"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.16"

// Play Framework 3.0.4 uses Pekko (not Akka)
val playVersion = "3.0.4"
val pekkoVersion = "1.0.2"
val pekkoHttpVersion = "1.0.1"

libraryDependencies ++= Seq(
  // Play WS Client (includes Pekko dependencies)
  "org.playframework" %% "play-ws-standalone" % "3.0.4",
  "org.playframework" %% "play-ws-standalone-json" % "3.0.4",

  // Pekko Streams (for multipart parsing)
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-actor" % pekkoVersion,

  // Pekko HTTP (for multipart support)
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,

  // Play JSON
  "org.playframework" %% "play-json" % "3.0.4",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
  "org.mockito" %% "mockito-scala" % "1.17.30" % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

// Test options
Test / parallelExecution := false
Test / fork := true
