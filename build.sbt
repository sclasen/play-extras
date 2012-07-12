name := "play-extras"

resolvers += "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases"

libraryDependencies ++= Seq(
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



