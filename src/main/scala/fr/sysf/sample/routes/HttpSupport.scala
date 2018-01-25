package fr.sysf.sample.routes

import java.util.UUID

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import fr.sysf.sample.routes.HttpSupport.{EntityNotFoundException, ErrorResponse, InvalidInputException, NotAuthorizedException}
import spray.json._


object HttpSupport {

  // Error body
  case class ErrorResponse(code: Int, `type`: String, message: Option[String] = None, detail: Option[Map[String, String]] = None)

  // Exception
  case class EntityNotFoundException(id: UUID, message: Option[String] = None) extends RuntimeException

  case class NotAuthorizedException(id: UUID, message: Option[String] = None) extends RuntimeException

  case class InvalidInputException(message: Option[String] = None, detail: Map[String, String]) extends RuntimeException

  val healthCheckRoute: Route =
    path("healthcheck") {
      get {
        complete("ok")
      }
    }
}

trait HttpSupport extends DefaultJsonFormats with Directives with CorsSupport {

  implicit val errorResponse: RootJsonFormat[ErrorResponse] = jsonFormat4(ErrorResponse)


  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case _: ArithmeticException =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "1Bad numbers, bad result!!!"))
      }

    case e: EntityNotFoundException =>
      complete(StatusCodes.NotFound, ErrorResponse(code = StatusCodes.NotFound.intValue, `type` = e.getClass.getSimpleName, message = Some(s"entity not found with id : ${e.id}")))

    case e: NotAuthorizedException =>
      complete(StatusCodes.Forbidden, ErrorResponse(code = StatusCodes.Forbidden.intValue, `type` = e.getClass.getSimpleName, message = e.message))

    case e: InvalidInputException =>
      complete(StatusCodes.BadRequest, ErrorResponse(code = StatusCodes.BadRequest.intValue, `type` = e.getClass.getSimpleName, message = e.message, detail = Some(e.detail)))

    case e: Exception =>
      complete(StatusCodes.InternalServerError, ErrorResponse(code = StatusCodes.InternalServerError.intValue, `type` = e.getClass.getSimpleName, message = Some(e.getMessage)))
  }

  implicit def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handleNotFound {
      complete(StatusCodes.NotFound, ErrorResponse(code = StatusCodes.NotFound.intValue, `type` = "NotFoundRejection", message = Some("The requested resource could not be found.")))
    }
    .handle {
      case AuthorizationFailedRejection =>
        complete(StatusCodes.Forbidden, ErrorResponse(code = StatusCodes.Forbidden.intValue, `type` = "AuthorizationFailedRejection", message = Some("You're out of your depth!")))

      case ValidationRejection(msg, _) =>
        complete(StatusCodes.BadRequest, ErrorResponse(code = StatusCodes.BadRequest.intValue, `type` = "ValidationRejection", message = Some(s"That wasn't valid! $msg")))
    }
    .handleAll[AuthenticationFailedRejection] {_ =>
      complete(StatusCodes.Forbidden, ErrorResponse(code = StatusCodes.Forbidden.intValue, `type` = "AuthorizationFailedRejection", message = Some("The resource requires authentication, which was not supplied with the request")))
    //  case CredentialsRejected => complete(StatusCodes.Forbidden, ErrorResponse(code = StatusCodes.Forbidden.intValue, `type` = "AuthorizationFailedRejection", message = Some("The supplied authentication is invalid")))
    }
    .handleAll[MethodRejection] { methodRejection =>
    complete(StatusCodes.MethodNotAllowed, ErrorResponse(code = StatusCodes.MethodNotAllowed.intValue, `type` = "MethodRejection", message = Some(s"Can't do that! Supported: ${methodRejection.map(_.supported.name).mkString(" or ")}!")))
  }
    .result()


  /*
  def requestTimeout: Timeout = {
    val t = conf.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }

  implicit val timeout: Timeout = requestTimeout
  */
}

trait CorsSupport {

  private val addAccessControlHeaders: Directive0 = mapResponseHeaders { headers =>
    `Access-Control-Allow-Origin`.* +:
      `Access-Control-Allow-Credentials`(true) +:
      `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With") +:
      headers
  }

  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(200).withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
  }

  def corsHandler(r: Route): Route = addAccessControlHeaders {
    preflightRequestHandler ~ r
  }
}