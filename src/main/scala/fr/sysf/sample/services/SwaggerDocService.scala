package fr.sysf.sample.services

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import fr.sysf.sample.CorsSupport
import io.swagger.models.ExternalDocs
import io.swagger.models.auth.OAuth2Definition

object SwaggerDocService extends SwaggerHttpService with CorsSupport {

  override val apiClasses = Set(classOf[PrizeService], classOf[GameService])
  override val info = Info(version = "1.0")
  override val externalDocs = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new OAuth2Definition())
  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")

}