scalaVersion := "2.13.5"

val http4sVersion = "0.21.22"
val circeVersion = "0.12.3"

lazy val fin = project
  .in(file("."))
  .settings(
    scalaVersion := "2.13.5",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "2.5.0",
    libraryDependencies += "com.github.ghostdogpr" %% "caliban" % "0.9.5",
    libraryDependencies += "com.github.ghostdogpr" %% "caliban-http4s" % "0.9.5",
    libraryDependencies += "com.github.ghostdogpr" %% "caliban-cats" % "0.9.5",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )
