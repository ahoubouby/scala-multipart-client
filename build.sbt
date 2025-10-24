// ========================================
// Project Metadata
// ========================================

name := "scala-multipart-client"

organization := "io.github.ahoubouby"

version := "0.1.0"

scalaVersion := "2.13.16"

// ========================================
// Library Information
// ========================================

description := "A generic, type-safe Scala library for parsing multipart HTTP responses"

homepage := Some(url("https://github.com/ahoubouby/scala-multipart-client"))

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))

developers := List(
  Developer(
    id = "ahoubouby",
    name = "Ahmed Houbouby",
    email = "ahoubouby@example.com",
    url = url("https://github.com/ahoubouby"),
  ),
)

scmInfo := Some(
  ScmInfo(
    url("https://github.com/ahoubouby/scala-multipart-client"),
    "scm:git:git@github.com:ahoubouby/scala-multipart-client.git",
  ),
)

// ========================================
// Dependencies
// ========================================

val playVersion      = "3.0.4"
val pekkoVersion     = "1.0.2"
val pekkoHttpVersion = "1.0.1"

libraryDependencies ++= Seq(
  // Play WS Client (includes Pekko dependencies)
  "org.playframework" %% "play-ws-standalone"      % playVersion,
  "org.playframework" %% "play-ws-standalone-json" % playVersion,
  // Pekko Streams (for multipart parsing)
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-actor"  % pekkoVersion,
  // Pekko HTTP (for multipart support)
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  // Play JSON
  "org.playframework" %% "play-json" % playVersion,
  // Logging
  "ch.qos.logback"             % "logback-classic" % "1.4.14",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.5",
  // Testing (Test scope only)
  "org.scalatest"          %% "scalatest"           % "3.2.18"      % Test,
  "org.scalatestplus.play" %% "scalatestplus-play"  % "7.0.1"       % Test,
  "org.mockito"            %% "mockito-scala"       % "1.17.30"     % Test,
  "org.apache.pekko"       %% "pekko-stream-testkit" % pekkoVersion % Test,
  "org.apache.pekko"       %% "pekko-testkit"       % pekkoVersion % Test,
)

// ========================================
// Compiler Options
// ========================================

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfatal-warnings", // Treat warnings as errors
)

// ========================================
// Test Configuration
// ========================================

Test / parallelExecution := false
Test / fork              := true
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

// ========================================
// Publishing Configuration
// ========================================

// Publish to Sonatype (Maven Central)
publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

// Required for Sonatype
publishMavenStyle := true

// Don't publish test artifacts
Test / publishArtifact := false

// POM settings for Maven Central
pomIncludeRepository := { _ => false }

// Additional POM information
pomExtra :=
  <issueManagement>
    <system>GitHub</system>
    <url>https://github.com/ahoubouby/scala-multipart-client/issues</url>
  </issueManagement>

// ========================================
// Artifact Generation
// ========================================

// Generate scaladoc
Compile / doc / scalacOptions ++= Seq(
  "-doc-title",
  "Scala Multipart Client",
  "-doc-version",
  version.value,
)

// Package source code
Compile / packageSrc / publishArtifact := true

// Package scaladoc
Compile / packageDoc / publishArtifact := true

// ========================================
// Cross Building (Optional)
// ========================================

// Uncomment to enable cross-building for multiple Scala versions
// crossScalaVersions := Seq("2.13.16", "3.3.1")

// ========================================
// GitHub Packages Publishing (Alternative)
// ========================================

// Uncomment to publish to GitHub Packages instead of Maven Central
// publishTo := Some(
//   "GitHub Package Registry" at "https://maven.pkg.github.com/ahoubouby/scala-multipart-client"
// )
// publishMavenStyle := true
// credentials += Credentials(
//   "GitHub Package Registry",
//   "maven.pkg.github.com",
//   "ahoubouby",
//   sys.env.getOrElse("GITHUB_TOKEN", "")
// )

// ========================================
// Custom Tasks
// ========================================

// Task to check if ready for release
lazy val releaseCheck = taskKey[Unit]("Check if project is ready for release")

releaseCheck := {
  val log = streams.value.log
  log.info("Checking release readiness...")

  // Check version is not snapshot
  if (version.value.endsWith("-SNAPSHOT")) {
    sys.error("Cannot release a SNAPSHOT version. Update version in build.sbt")
  }

  // Check tests pass
  (Test / test).value

  log.info("✓ Project is ready for release")
}
