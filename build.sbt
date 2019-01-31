name := "netty-scala-azinman-gist"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "latest.release" % Test,
  "org.jboss.netty" % "netty" % "3.2.10.Final",
  "org.log4s" %% "log4s" % "1.6.1",
  "org.slf4j" % "slf4j-simple" % "1.7.25"
)
