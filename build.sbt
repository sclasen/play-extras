name := "play-extras"

libraryDependencies ++= Seq(
   "play" %% "play" % "2.0.2",
   "play" %% "anorm" % "2.0.2",
   "com.typesafe.akka" % "akka-actor" % "2.0.2",
   "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
   "org.scalaz" %% "scalaz-core" % "6.0.4",
   "redis.clients" % "jedis" % "2.1.0",
    "org.slf4j"                         %    "slf4j-api"                %   "1.6.4",
    "ch.qos.logback"                    %    "logback-core"             %   "1.0.3",
    "ch.qos.logback"                    %    "logback-classic"          %   "1.0.3"
)



