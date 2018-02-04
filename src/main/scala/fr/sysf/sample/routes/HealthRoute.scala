package fr.sysf.sample.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import buildinfo.BuildInfo
import fr.sysf.sample.utils.CorsSupport

trait HealthRoute extends Directives with CorsSupport {

  val healthCheckRoute: Route = corsHandler(
    path("health") {
      get {
        complete(HttpResponse(StatusCodes.OK, entity =
          HttpEntity(MediaTypes.`application/json`,
            """{
              "status": "UP"
              }""".stripMargin))
        )
      }
    } ~
      path("info") {
        get {
          complete(HttpResponse(StatusCodes.OK, entity =
            HttpEntity(MediaTypes.`application/json`,
              s"""{
              "group": "${BuildInfo.organization}",
              "name": "${BuildInfo.name}",
              "version": "${BuildInfo.version}",
              "buildTime": "${BuildInfo.buildTime}",
              "buildScalaVersion": "${BuildInfo.scalaVersion}",
              "buildSbtVersion": "${BuildInfo.sbtVersion}",
              "description": "${BuildInfo.description}"
              }""".stripMargin))
          )
        }
      }
  )
}
