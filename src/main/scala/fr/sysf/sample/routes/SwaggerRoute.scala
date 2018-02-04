package fr.sysf.sample.routes

import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, PathMatcher0, PathMatchers, Route}
import com.github.swagger.akka.model.Info
import com.github.swagger.akka.{CustomMediaTypes, SwaggerGenerator, SwaggerHttpService}
import fr.sysf.sample.utils.CorsSupport
import io.swagger.models.ExternalDocs
import io.swagger.models.auth.BasicAuthDefinition

trait SwaggerRoute extends Directives with SwaggerGenerator with CorsSupport {

  import SwaggerHttpService._

  override val apiClasses = Set(classOf[BoPrizeRoute], classOf[BoGameRoute], classOf[PartnerRoute])
  override val info = Info(version = "1.0", description = "3 users : \n- admin_fr / p4ssw0rd\n- admin_ca / p4ssw0rd\n- admin_jp / p4ssw0rd")
  override val externalDocs = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
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

  def apiDocsBase(path: String): PathMatcher0 = PathMatchers.separateOnSlashes(removeInitialSlashIfNecessary(path))

}