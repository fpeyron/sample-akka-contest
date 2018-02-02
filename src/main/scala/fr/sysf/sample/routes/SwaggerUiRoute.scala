package fr.sysf.sample.routes

import akka.http.scaladsl.server.Directives

trait SwaggerUiRoute extends Directives {

  val swaggerUiRoute = path("swagger") {
    getFromResource("swagger/index.html")
  } ~ getFromResourceDirectory("swagger")
}
