import scala.sys.process._

val dataVersion = "2019b"
val softwareVersion = "7-SNAPSHOT"
val `commons-compress` = Seq(
  "org.apache.commons" % "commons-compress" % "1.18",
  "com.github.luben" % "zstd-jni" % "1.3.7-1"
)
val commonSettings = Seq(
  organization := "net.iakovlev",
  sonatypeProfileName := "net.iakovlev",
  version := s"$dataVersion.$softwareVersion",
  crossPaths := false,
  autoScalaLibrary := false,
  publishMavenStyle := true,
  javacOptions ++= Seq("-Xdoclint:none")
)

lazy val timeshape = (project in file("."))
  .settings(commonSettings)
  .aggregate(core, builder, testApp, `geojson-proto`, benchmarks)
  .settings(skip in publish := true)

lazy val builderArgument = settingKey[String](
  "Argument to pass to builder, either local path to source data file or version to download")

lazy val releaseTask = taskKey[Unit](
  "Publishes an artifact and optionally makes a release if version is not a snapshot")

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    builderArgument := dataVersion,
    libraryDependencies ++= Seq(
      "com.esri.geometry" % "esri-geometry-api" % "2.2.1",
      "junit" % "junit" % "4.12" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test
        exclude ("junit", "junit-dep"),
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "com.fasterxml.jackson.core" % "jackson-core" % "2.9.6"
    ) ++ `commons-compress`,
    name := "timeshape",
    publishTo := sonatypePublishTo.value,
    packageOptions in (Compile, packageBin) += Package.ManifestAttributes("Automatic-Module-Name" -> "net.iakovlev.timeshape"),
    resourceGenerators in Compile += Def.taskDyn {
      val log = streams.value.log
      val outputPath = (resourceManaged in Compile).value
      val outputFile = outputPath / "data.tar.zstd"
      outputPath.mkdirs()
      if (!outputFile.exists()) {
        log.info("Timeshape resource doesn't exist, creating")
        val command =
          s"java -jar ${(builder / assembly).value} ${builderArgument.value} ${outputFile.getAbsolutePath}"
        log.info(s"running $command")
        command.!
      } else {
        log.info("Timeshape resource exists, skipping creation")
      }
      Def.task(Seq(outputFile))
    }.taskValue,
    releaseTask := {
      publish.value
      val buildState = state.value
      if (!isSnapshot.value) Command.process("sonatypeRelease", buildState)
    }
  )
  .dependsOn(`geojson-proto`)

lazy val `geojson-proto` = (project in file("geojson-proto"))
  .settings(commonSettings)
  .settings(
    publishTo := sonatypePublishTo.value,
    version := "1.1.0-SNAPSHOT",
    PB.targets in Compile := Seq(
      PB.gens.java("3.10.0") -> (sourceManaged in Compile).value
    )
  )

lazy val builder = (project in file("builder"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.grundid.opendatalab" % "geojson-jackson" % "1.8.1"
    ) ++ `commons-compress`,
    name := "timeshape-builder",
    skip in publish := true
  )
  .dependsOn(`geojson-proto`)

lazy val testApp = (project in file("test-app"))
  .settings(commonSettings)
  .settings(
    mainClass in assembly := Some("net.iakovlev.timeshape.testapp.Main"),
    skip in publish := true,
    libraryDependencies ++= Seq("org.openjdk.jol" % "jol-core" % "0.9",
                                "ch.qos.logback" % "logback-classic" % "1.2.3")
  )
  .dependsOn(core)

lazy val benchmarks = (project in file("benchmarks"))
  .settings(commonSettings)
  .settings(skip in publish := true)
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
