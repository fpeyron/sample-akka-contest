import java.time.Instant

import com.typesafe.sbt.packager.docker.Cmd
import sbt.enablePlugins

organization := "com.betc.danon"
name := "fusion-game"
description := "Fusion provider Game"

lazy val akkaVersion = "2.5.9"
lazy val akkaHttpVersion = "10.0.11"
lazy val ConstructrAkka   = "0.18.1"

resolvers += Resolver.bintrayRepo("everpeace", "maven")


// --- akka core
libraryDependencies += "com.typesafe.akka"            %% "akka-actor"                     % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-stream"                    % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-slf4j"                     % akkaVersion
// --- akka http
libraryDependencies += "com.typesafe.akka"            %% "akka-http"                      % akkaHttpVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-parsing"                   % akkaHttpVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-http-spray-json"           % akkaHttpVersion
// --- swagger generator
libraryDependencies += "com.github.swagger-akka-http" %% "swagger-akka-http"              % "0.10.1"
libraryDependencies += "io.swagger"                   % "swagger-jaxrs"                   % "1.5.16"
// --- akka cluster
libraryDependencies += "com.typesafe.akka"            %% "akka-cluster"                   % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-cluster-metrics"           % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-cluster-sharding"          % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-remote"                    % akkaVersion
libraryDependencies += "com.typesafe.akka"            %% "akka-cluster-tools"             % akkaVersion
libraryDependencies += "com.lightbend.akka"           %% "akka-management-cluster-http"   % "0.5"
// --- akka cluster contructr for redis
libraryDependencies += "de.heikoseeberger"            %% "constructr"                     % ConstructrAkka
libraryDependencies += "com.github.everpeace"         %% "constructr-coordination-redis"  % "0.0.4"
// --- Akka Persistent / Mysql
libraryDependencies += "com.github.dnvriend"          %% "akka-persistence-jdbc"          % "3.2.0"
//libraryDependencies += "com.h2database"               % "h2"                              % "1.4.193"
libraryDependencies += "mysql"                        % "mysql-connector-java"            % "6.0.6"
// ---Serializer for akka
libraryDependencies += "com.github.romix.akka"        %% "akka-kryo-serialization"        % "0.5.2"

libraryDependencies += "ch.qos.logback"               % "logback-classic"                 % "1.2.+"

// ---Test
libraryDependencies += "com.typesafe.akka"            %% "akka-testkit"                   % akkaVersion % Test
libraryDependencies += "com.typesafe.akka"            %% "akka-stream-testkit"            % akkaVersion % Test



// Force dependencies to remove WARN in log during compilation
dependencyOverrides += "com.typesafe.akka"            %% "akka-stream"                    % akkaVersion
dependencyOverrides += "com.typesafe.akka"            %% "akka-actor"                     % akkaVersion
dependencyOverrides += "com.typesafe.akka"            %% "akka-remote"                    % akkaVersion
dependencyOverrides += "de.heikoseeberger"            %% "constructr-coordination"        % ConstructrAkka
dependencyOverrides += "org.codehaus.plexus"          % "plexus-utils"                    % "3.0.17"
dependencyOverrides += "com.google.guava"             % "guava"                           % "22.0"


// ----------------
// Run
// ----------------
mainClass in (Compile, run) := Some("com.betc.danon.game.Main")
fork in run := true


// ----------------
// Generate BuildInd
// ----------------
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion, description, "buildTime" -> Instant.now)


// ----------------
// Docker packaging
// ----------------
enablePlugins(DockerPlugin, JavaAppPackaging)

packageName               in Docker := s"danon-${name.value}"
version                   in Docker := version.value
maintainer                in Docker   := "technical support <florent.peyron@ext.betc.com>"
dockerBaseImage            := "openjdk:8u151-jre-alpine"
dockerCommands := Seq(
  dockerCommands.value.head,
  Cmd("RUN", "apk add --no-cache curl bash && rm -rf /var/cache/apk/*"),
  Cmd("ADD", "opt /opt"),
  Cmd("RUN", "chown -R daemon:daemon /opt"),
  Cmd("ENV", "SERVICE_AKKA_PORT 2550"),
  Cmd("ENV", "JAVA_OPTS '-Xmx256m -Xms256m'"),
  Cmd("ENV", "REDIS_HOST 'redis'"),
  Cmd("ENV", "REDIS_PORT 6379"),
  Cmd("ENV", "MYSQL_HOST 'mysql'"),
  Cmd("ENV", "MYSQL_PORT 3306"),
  Cmd("ENV", "MYSQL_DATABASE 'mysql'"),
  Cmd("ENV", "MYSQL_USER 'root'"),
  Cmd("ENV", "MYSQL_PASSWORD 'root'")
) ++ dockerCommands.value.tail.filter{case Cmd("ADD", _ ) => false  case _ => true }
dockerExposedPorts         := Seq(8080,2550,5000)
dockerUpdateLatest        := true
dockerEntrypoint          := Seq("sh","-c","HOST_IP=$(/usr/bin/curl -s --connect-timeout 1 169.254.169.254/latest/meta-data/local-ipv4) && SERVICE_AKKA_HOST=$HOST_IP ; echo SERVICE_AKKA_HOST:$SERVICE_AKKA_HOST ; " + s"bin/fusion-game -Dconfig.resource=application.conf" + " -Dakka.remote.netty.tcp.hostname=$SERVICE_AKKA_HOST")

