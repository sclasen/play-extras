import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform._


object Build extends Build {

  val playextras = (Project("play-extras", file(".")) settings(
    organization := "com.sclasen",
    name := "play-extras",
    version := "0.2.13-SNAPSHOT",
    scalaVersion := "2.10.0",
    crossScalaVersions := Seq("2.10.0"),
    libraryDependencies ++= dependencies,
    resolvers ++= Seq("TypesafeMaven" at "http://repo.typesafe.com/typesafe/maven-releases", "whydoineedthis" at "http://repo.typesafe.com/typesafe/releases")
    ) settings(publishSettings:_*) settings(testSettings:_*) settings(scalariformSettings:_*)).settings(scalacOptions ++= Seq("-feature", "-deprecation"))

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
    testOptions in Test += Tests.Argument("console"),
    publishArtifact in Test := true
  )

  def dependencies = Seq(
    "play" %% "play" % "2.1.0",
    "play" %% "anorm" % "2.1.0",
    "play" %% "play-jdbc" % "2.1.0",
    "com.typesafe.akka" %% "akka-actor" % "2.1.0",
    "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
    "org.scalaz" %% "scalaz-core" % "6.0.4",
    "redis.clients" % "jedis" % "2.1.0",
    "org.slf4j"                         %    "slf4j-api"                %   "1.6.6",
    "ch.qos.logback"                    %    "logback-core"             %   "1.0.7",
    "ch.qos.logback"                    %    "logback-classic"          %   "1.0.7",
    "org.jasypt" % "jasypt" % "1.9.0",
    "org.bouncycastle" % "bcpg-jdk15on" % "1.47",
    "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.47",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.specs2"                        %%   "specs2"                   %   "1.13"      %  "test",
    "play" %% "play-test" % "2.1.0" % "test"
  )


}
