lazy val commonSettings = Seq(
  name := "netty-scala-azinman-gist",

  organization := "com.mypackage",

  version := "1.0",

  scalaVersion := "2.11.8",

  resolvers ++= Seq(
     "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  ),

  libraryDependencies ++= Seq(
    "com.novocode" % "junit-interface" % "latest.release" % Test,
    "org.jboss.netty" % "netty" % "3.2.10.Final",
    "org.log4s" %% "log4s" % "1.6.1",
    "org.slf4j" % "slf4j-simple" % "1.7.25"
  )
)


lazy val server = (project in file("server")).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("com.mypackage.benchmark.BenchmarkServerMain"),
    assemblyJarName in assembly := "netty-benchmark-server.jar"
  )

lazy val client = (project in file("client")).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("com.mypackage.benchmark.BenchmarkClientMain"),
    assemblyJarName in assembly := "netty-benchmark-client.jar"
  )
