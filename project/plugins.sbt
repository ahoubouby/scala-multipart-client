// ========================================
// Build Plugins
// ========================================

addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.18.0")
addSbtPlugin("com.github.sbt"           % "sbt-native-packager" % "1.9.16")

// ========================================
// Code Quality Plugins
// ========================================

addSbtPlugin("org.scalameta"  % "sbt-scalafmt"  % "2.5.2")
addSbtPlugin("org.scoverage"  % "sbt-scoverage" % "2.0.11")

// ========================================
// Publishing Plugins
// ========================================

// For signing artifacts (required for Maven Central)
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.2.1")

// For publishing to Sonatype/Maven Central
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.10.0")

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
