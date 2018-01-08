import sbt.enablePlugins

organization := "com.betc.sample"
name := "sample-contest"

//scalaVersion := "2.12.4"

val akkaVersion = "2.5.8"
val akkaHttpVersion = "10.0.11"

libraryDependencies += "com.typesafe.akka"             %% "akka-http"              % akkaHttpVersion
libraryDependencies += "com.typesafe.akka"             %% "akka-parsing"           % akkaHttpVersion
libraryDependencies += "com.typesafe.akka"             %% "akka-http-spray-json"   % akkaHttpVersion

libraryDependencies += "com.typesafe.akka"             %% "akka-actor"             % akkaVersion
libraryDependencies += "com.typesafe.akka"             %% "akka-stream"            % akkaVersion
libraryDependencies += "com.typesafe.akka"             %% "akka-slf4j"             % akkaVersion

libraryDependencies += "com.github.swagger-akka-http"  %% "swagger-akka-http"      % "0.10.1"
libraryDependencies += "io.swagger"                    % "swagger-jaxrs"           % "1.5.16"


// ----------------
// Docker packaging
// ----------------
enablePlugins(DockerPlugin, JavaAppPackaging)

packageName               in Docker := name.value
version                   in Docker := version.value
maintainer                in Docker := "florent.peyron@gmail.com"
//dockerCmd                  := Seq("apt-get update && apt-get install -y iputils-ping")
dockerBaseImage           := "openjdk:8u151-jre-slim"
dockerExposedPorts        := Seq(8080)
dockerUpdateLatest        := true
