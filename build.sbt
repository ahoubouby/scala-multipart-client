// ========================================
// Build-wide settings
// ========================================
ThisBuild / organization := "io.github.ahoubouby"
ThisBuild / scalaVersion  := "2.13.16"

// ----------------------------------------
// Shared dependency versions
// ----------------------------------------
lazy val playVersion      = "3.0.4"
lazy val pekkoVersion     = "1.0.3"   // unified
lazy val pekkoHttpVersion = "1.0.1"   // override Pekko deps to 1.0.3 via dependencyOverrides

// ----------------------------------------
// Shared settings for all subprojects
// ----------------------------------------
lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding","UTF-8",
    "-deprecation","-feature","-unchecked","-Xlint",
    "-Ywarn-dead-code","-Ywarn-numeric-widen","-Ywarn-value-discard",
    "-Xfatal-warnings",
    "-nowarn"
  ),
  Test / parallelExecution := false,
  Test / fork              := true,
  Test / testOptions      += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
)

// ========================================
// Root project = your library
// ========================================
lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name        := "scala-multipart-client",
    version     := "0.1.0",
    description := "A generic, type-safe Scala library for parsing multipart HTTP responses",

    homepage := Some(url("https://github.com/ahoubouby/scala-multipart-client")),
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        id = "ahoubouby",
        name = "Ahmed Houbouby",
        email = "ahoubouby@example.com",
        url = url("https://github.com/ahoubouby"),
      )
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/ahoubouby/scala-multipart-client"),
        "scm:git:git@github.com:ahoubouby/scala-multipart-client.git",
      )
    ),

    // -------- Dependencies (library only) --------
    libraryDependencies ++= Seq(
      // Play WS Client (includes Pekko dependencies)
      "org.playframework" %% "play-ws-standalone"      % playVersion,
      "org.playframework" %% "play-ws-standalone-json" % playVersion,
      // Pekko Streams / Actors
      "org.apache.pekko"  %% "pekko-stream"            % pekkoVersion,
      "org.apache.pekko"  %% "pekko-actor"             % pekkoVersion,
      // Pekko HTTP (multipart)
      "org.apache.pekko"  %% "pekko-http"              % pekkoHttpVersion,
      // Play JSON
      "org.playframework" %% "play-json"               % playVersion,
      // Logging
      "ch.qos.logback"     % "logback-classic"         % "1.4.14",
      "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.5",
      // Test
      "org.scalatest"           %% "scalatest"            % "3.2.18" % Test,
      "org.scalatestplus.play"  %% "scalatestplus-play"   % "7.0.1"  % Test,
      "org.mockito"             %% "mockito-scala"        % "1.17.30" % Test,
      "org.apache.pekko"        %% "pekko-stream-testkit" % pekkoVersion % Test,
      "org.apache.pekko"        %% "pekko-testkit"        % pekkoVersion % Test,
    ),

    dependencyOverrides ++= Seq(
      "org.apache.pekko" %% "pekko-actor"                 % pekkoVersion,
      "org.apache.pekko" %% "pekko-stream"                % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor-typed"           % pekkoVersion,
      "org.apache.pekko" %% "pekko-slf4j"                 % pekkoVersion,
      "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
      "org.apache.pekko" %% "pekko-protobuf-v3"           % pekkoVersion,
      "org.apache.pekko" %% "pekko-testkit"               % pekkoVersion,
      "org.apache.pekko" %% "pekko-stream-testkit"        % pekkoVersion
    ),

    // -------- Publishing (library only) --------
    publishTo := {
      val nexus = "https://s01.oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle    := true,
    Test / publishArtifact := false,
    pomIncludeRepository := { _ => false },
    pomExtra :=
      <issueManagement>
        <system>GitHub</system>
        <url>https://github.com/ahoubouby/scala-multipart-client/issues</url>
      </issueManagement>,

    // -------- Artifacts --------
    Compile / doc / scalacOptions ++= Seq(
      "-doc-title","Scala Multipart Client",
      "-doc-version", version.value
    ),
    Compile / packageSrc / publishArtifact := true,
    Compile / packageDoc / publishArtifact := true,

    // -------- Custom tasks --------
    releaseCheck := {
      val log = streams.value.log
      log.info("Checking release readiness...")
      if (version.value.endsWith("-SNAPSHOT"))
        sys.error("Cannot release a SNAPSHOT version. Update version in build.sbt")
      (Test / test).value
      log.info("✓ Project is ready for release")
    }
  )

// ========================================
// Examples subproject (depends on the library)
// ========================================
lazy val examples = (project in file("examples"))
  .dependsOn(root)
  .settings(commonSettings)
  .settings(
    name := "scala-multipart-client-examples",
    // don't ever publish this module
    publish / skip := true,
    // add any extra deps examples need at runtime/test only
    libraryDependencies ++= Seq(
      // Play WS Standalone (AHC implementation)
      "org.playframework" %% "play-ahc-ws-standalone" % playVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.14" % Runtime
    )
  )

// Optionally, aggregate so running `test` at the root includes `examples` tests
// (remove `.aggregate(examples)` if you prefer isolation)
// ========================================
// Keys
// ========================================
lazy val releaseCheck = taskKey[Unit]("Check if project is ready for release")
