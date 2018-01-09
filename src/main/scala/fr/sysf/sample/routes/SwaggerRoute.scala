package fr.sysf.sample.routes

import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, PathMatchers, Route}
import com.github.swagger.akka.model.Info
import com.github.swagger.akka.{CustomMediaTypes, SwaggerGenerator, SwaggerHttpService}
import io.swagger.models.ExternalDocs
import io.swagger.models.auth.OAuth2Definition

trait SwaggerRoute extends Directives with SwaggerGenerator with CorsSupport {

  import SwaggerHttpService._

  def apiDocsBase(path: String) = PathMatchers.separateOnSlashes(removeInitialSlashIfNecessary(path))
  override val apiClasses = Set(classOf[PrizeRoute], classOf[GameRoute])
  override val info = Info(version = "1.0")
  override val externalDocs = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new OAuth2Definition())
  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")


  def swaggerRoute: Route = {
    val base = apiDocsBase(apiDocsPath)
    path(base / "swagger.json") {
      get {
        complete(HttpEntity(MediaTypes.`application/json`, generateSwaggerJson))
      }
    } ~
      path(base / "swagger.yaml") {
        get {
          complete(HttpEntity(CustomMediaTypes.`text/vnd.yaml`, generateSwaggerYaml))
        }
      }
  }

}