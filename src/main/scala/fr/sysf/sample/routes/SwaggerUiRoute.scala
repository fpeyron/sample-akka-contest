package fr.sysf.sample.routes

import akka.http.scaladsl.server.{Directives, Route}
import fr.sysf.sample.utils.CorsSupport

trait SwaggerUiRoute extends Directives with CorsSupport {

  val swaggerUiRoute: Route = path("swagger") {
    getFromResource("swagger/index.html")
  } ~ getFromResourceDirectory("swagger")
}
