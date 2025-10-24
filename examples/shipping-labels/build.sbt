name := "shipping-labels-example"

version := "1.0.0"

scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  // Scala Multipart Client library
  "io.github.ahoubouby" %% "scala-multipart-client" % "0.1.0",

  // HTTP client (Play WS Standalone)
  "org.playframework" %% "play-ws-standalone" % "3.0.4",
  "org.playframework" %% "play-ws-standalone-json" % "3.0.4",
  // Play WS Standalone (AHC implementation)
  "org.playframework" %% "play-ahc-ws-standalone" % "3.0.4",
  "org.playframework" %% "play-ws-standalone-json" % "3.0.4",

  // Pekko for actor system
  "org.apache.pekko" %% "pekko-actor" % "1.0.2",
  "org.apache.pekko" %% "pekko-stream" % "1.0.2",
)
