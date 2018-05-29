
val kindProjector = "org.spire-math" %% "kind-projector" % "0.9.4"

scalaVersion in ThisBuild := "2.12.4"

libraryDependencies ++= Seq(
  "com.slamdata" %% "matryoshka-core" % "0.21.3",
  "org.apache.avro" % "avro" % "1.8.2"
)

resolvers += Resolver.sonatypeRepo("releases")

val validationVersion = "2.1.0"

// validation library
libraryDependencies ++= Seq(
  "io.github.jto" %% "validation-core"      % validationVersion,
 // "io.github.jto" %% "validation-playjson"  % validationVersion,
  "io.github.jto" %% "validation-jsonast"   % validationVersion,
)

libraryDependencies += "com.codecommit" %% "shims" % "1.2.1"


lazy val fakeSpark = project in file("spark")

lazy val lc2018 = project in file(".") dependsOn fakeSpark

scalafmtOnCompile := true

addCompilerPlugin(kindProjector)