organization := "org.hibiup"
version := "0.0.1"

lazy val validation = {
    val scalaTestVersion = "3.0.8"
    val scalaCheckVersion = "1.14.0"

    Seq(
        "org.scalacheck" %% "scalacheck"                  % scalaCheckVersion,
        "org.scalatest"  %% "scalatest"                   % scalaTestVersion
    )
}

lazy val cats = Seq(
    "org.typelevel" %% "cats-core",
    "org.typelevel" %% "cats-free",
    "org.typelevel" %% "cats-effect"
)

lazy val logging = {
    val logBackVersion = "1.2.3"
    val scalaLoggingVersion = "3.9.2"

    Seq(
        "ch.qos.logback" % "logback-classic" % logBackVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
    )
}

lazy val akka = Seq(
    "com.typesafe.akka"   %% "akka-actor",
    "com.typesafe.akka"   %% "akka-stream"
)

lazy val dependencies = {
    val catsVersion = "2.0.0-RC2"
    val akkaVersion = "2.5.25"

    validation.map(_ % Test) ++ logging ++ cats.map(_ % catsVersion) ++ akka.map(_ % akkaVersion)
}

lazy val root = project.in(file(".")).settings(
    name := "EffectResource",
    scalaVersion := "2.13.0",
    libraryDependencies ++= dependencies,
    scalacOptions ++= Seq(
        "-language:higherKinds",
        "-deprecation",
        "-encoding", "UTF-8",
        "-feature",
        "-language:_"
    )
)
