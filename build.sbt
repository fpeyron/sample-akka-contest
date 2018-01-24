import sbt.enablePlugins

organization := "com.betc.sample"
name := "sample-contest"

lazy val akkaVersion = "2.5.8"
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


// Force dependencies to remove WARN in log during compilation
dependencyOverrides += "com.typesafe.akka"            %% "akka-stream"                    % akkaVersion
dependencyOverrides += "com.typesafe.akka"            %% "akka-actor"                     % akkaVersion
dependencyOverrides += "de.heikoseeberger"            %% "constructr-coordination"        % ConstructrAkka
dependencyOverrides += "org.codehaus.plexus"          % "plexus-utils"                    % "3.0.17"
dependencyOverrides += "com.google.guava"             % "guava"                           % "20.0"

// ----------------
// Run
// ----------------
mainClass in (Compile, run) := Some("fr.sysf.sample.Main")
fork in run := true


// ----------------
// Docker packaging
// ----------------
enablePlugins(DockerPlugin, JavaAppPackaging)

packageName               in Docker := name.value
version                   in Docker := version.value
maintainer                in Docker   := "technical support <florent.peyron@gmail.com>"
dockerCmd                 := Seq("apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*")
dockerBaseImage           := "openjdk:8u151-jre"
dockerExposedPorts         := Seq(8080,2550,5000)
dockerUpdateLatest        := true
dockerEntrypoint           := Seq("sh","-c","HOST_IP=$(/usr/bin/curl -s --connect-timeout 1 169.254.169.254/latest/meta-data/local-ipv4) && SERVICE_AKKA_HOST=$HOST_IP ; echo SERVICE_AKKA_HOST:$SERVICE_AKKA_HOST ; " + s"bin/${name.value.toLowerCase} -Dconfig.resource=application.conf" + " -Dakka.remote.netty.tcp.hostname=$SERVICE_AKKA_HOST")
