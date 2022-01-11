
lazy val projectSettings = Seq(
    // version := "0.1.1-SNAPSHOT",
    git.formattedShaVersion := git.gitHeadCommit.value map {
        sha => "0.1-" + s"$sha".slice(0, 7)
      },
    organization := "io.kymeta"
  )

lazy val compilationSettings = Seq(
    scalaVersion := "2.11.12",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    compileOrder in Compile := CompileOrder.JavaThenScala,
    scalacOptions ++= Seq(
        "-deprecation",
        "-encoding", "UTF-8",
        "-feature",
        "-language:existentials",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-unchecked",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen"
      ),
    resolvers ++= Seq(
        "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
        "data.kymeta.io/artifactory" at "https://data.kymeta.io/artifactory/KCS-maven",
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
      )
  )

lazy val sparkSettings = Seq(
    sparkVersion := "2.2.2",
    sparkComponents := Seq("core", "sql")
  )

lazy val versions = new {
  val spark = "2.2.0"
  val scalatest = "2.2.4"
}

lazy val dependencySettings = libraryDependencies ++= Seq(
//  "com.google.guava" % "guava" % "20.0",
//  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % "2.7.9",
//  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.9",
  "com.microsoft.azure" % "azure-storage" % "7.0.0",
//  "joda-time" % "joda-time" % "2.10",
  "org.scalatest" %% "scalatest" % "3.0.5",
  "com.twosigma" %% "flint" % "0.4.0-kymeta-a040a05",
  "harsha2010" %% "magellan" % "1.0.6-kymeta-ed41fd8"
)

assemblyMergeStrategy in assembly := {
  case "git.properties"               => MergeStrategy.discard
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x => val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val data = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(projectSettings)
  .settings(compilationSettings)
  .settings(dependencySettings)
  .settings(sparkSettings)
  .settings(
    name := "data"
  )
