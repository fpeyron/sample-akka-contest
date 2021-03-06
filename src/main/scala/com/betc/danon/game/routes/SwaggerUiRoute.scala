package com.betc.danon.game.routes

import akka.http.scaladsl.server.{Directives, Route}

trait SwaggerUiRoute extends Directives {

  val swaggerUiRoute: Route = path("swagger") {
    getFromResource("swagger/index.html")
  } ~ getFromResourceDirectory("swagger")
}
