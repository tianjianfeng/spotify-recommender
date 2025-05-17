ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "spotify-recommender",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "3.5.5",
      "org.apache.spark" %% "spark-sql" % "3.5.5",
      "org.apache.spark" %% "spark-mllib" % "3.5.5"
    )
  )
