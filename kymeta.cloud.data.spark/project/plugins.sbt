resolvers ++= Seq(Resolver.sbtPluginRepo("releases"),
                  Resolver.jcenterRepo,
                  "Spark Package Main Repo" at "https://dl.bintray.com/spark-packages/maven",
                  "releases" at "http://oss.sonatype.org/content/repositories/releases")

addSbtPlugin("com.eed3si9n" %% "sbt-assembly" % "0.14.3")
addSbtPlugin("org.scalariform" %% "sbt-scalariform" % "1.6.0")
addSbtPlugin("org.spark-packages" %% "sbt-spark-package" % "0.2.6")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.1")
