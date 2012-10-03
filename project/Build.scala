import sbt._
import Keys._


object Build extends Build {

  val playextras = (Project("play-extras", file(".")) settings(
    organization := "com.sclasen",
    name := "play-extras",
    version := "0.1.2",
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.9.1"),
    libraryDependencies ++= dependencies
    ) settings(publishSettings:_*) settings(testSettings:_*) )

  def publishSettings: Seq[Setting[_]] = Seq(
    // If we want on maven central, we need to be in maven style.
    publishMavenStyle := true,
    publishArtifact in Test := false,
    // The Nexus repo we're publishing to.
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else                             Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    // Maven central cannot allow other repos.  We're ok here because the artifacts we
    // we use externally are *optional* dependencies.
    pomIncludeRepository := { x => false },
    // Maven central wants some extra metadata to keep things 'clean'.
    pomExtra := (

      <url>http://github.com/sclasen/play-extras</url>
      <licenses>
        <license>
          <name>The Apache Software License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
        <scm>
          <url>git@github.com:sclasen/play-extras.git</url>
          <connection>scm:git:git@github.com:sclasen/play-extras.git</connection>
        </scm>
        <developers>
          <developer>
            <id>sclasen</id>
            <name>Scott Clasen</name>
            <url>http://github.com/sclasen</url>
          </developer>
        </developers>)
  )

  def testSettings: Seq[Setting[_]] = Seq(
    testOptions in Test += Tests.Argument("junitxml"),
    testOptions in Test += Tests.Argument("console")
  )

  def dependencies = Seq(
    "play" %% "play" % "2.0.2",
    "play" %% "anorm" % "2.0.2",
    "com.typesafe.akka" % "akka-actor" % "2.0.2",
    "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
    "org.scalaz" %% "scalaz-core" % "6.0.4",
    "redis.clients" % "jedis" % "2.1.0",
    "org.slf4j"                         %    "slf4j-api"                %   "1.6.4",
    "ch.qos.logback"                    %    "logback-core"             %   "1.0.3",
    "ch.qos.logback"                    %    "logback-classic"          %   "1.0.3",
    "org.jasypt" % "jasypt" % "1.9.0",
    "org.bouncycastle" % "bcpg-jdk15on" % "1.47",
    "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.47",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.specs2"                        %%   "specs2"                   %   "1.9"      %  "test",
    "play" %% "play-test" % "2.0.2" % "test"
  )


}