scalaVersion in ThisBuild := "2.12.4"

libraryDependencies ++= Seq(
  "com.slamdata" %% "matryoshka-core" % "0.21.3",
  "com.slamdata" %% "matryoshka-scalacheck" % "0.21.3" % Test,
  "org.apache.avro" % "avro" % "1.8.2"
)

lazy val fakeSpark = project in file("spark")

lazy val lc2018 = project in file(".") dependsOn fakeSpark

resolvers += Resolver.sonatypeRepo("snapshots")

scalafmtOnCompile := true
