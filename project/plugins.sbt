resolvers += Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.1")

