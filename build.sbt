
val kindProjector = "org.spire-math" %% "kind-projector" % "0.9.7"

scalaVersion in ThisBuild := "2.12.4"

lazy val matryoshkaVersion = "0.21.3"
lazy val validationVersion = "2.1.0"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  "com.slamdata" %% "matryoshka-core" % matryoshkaVersion,
  "com.slamdata" %% "matryoshka-scalacheck" % matryoshkaVersion,
  "org.apache.avro" % "avro" % "1.8.2",
  // JTO validation library
  "io.github.jto" %% "validation-core" % validationVersion,
  "io.github.jto" %% "validation-jsonast" % validationVersion,
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

lazy val fakeSpark = project in file("spark")

lazy val lc2018 = project in file(".") dependsOn fakeSpark

scalafmtOnCompile := true

addCompilerPlugin(kindProjector)
