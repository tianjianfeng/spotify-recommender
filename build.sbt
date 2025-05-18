ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "spotify-recommender",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      "io.circe" %% "circe-generic" % "0.14.10",
      "org.apache.spark" %% "spark-core" % "3.5.5",
      "org.apache.spark" %% "spark-sql" % "3.5.5",
      "org.apache.spark" %% "spark-mllib" % "3.5.5"
    )
  )
