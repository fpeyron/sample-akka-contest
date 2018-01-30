package fr.sysf.sample.routes

import java.util.UUID

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, HttpResponse, StatusCodes, StatusCode}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import fr.sysf.sample.routes.HttpSupport._
import spray.json._


object HttpSupport {

  // Error body
  case class ErrorResponse(code: Int, `type`: String, message: Option[String] = None, detail: Option[Map[String, String]] = None)

  // Exception
  class FunctionalException(val statusCode: StatusCode, val `type`: Option[String] = None, val message: String) extends RuntimeException

  case class InvalidInputException(message: Option[String] = None, detail: Map[String, String]) extends RuntimeException


  case class GameIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = Some("GameNotFoundException"), message = s"game not found with id : $id")

  case class GamePrizeIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = Some("GamePrizeNotFoundException"), message = s"gamePrize not found for this game with id : $id")

  case class GameRefNotFoundException(country_code: String, code: String) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = Some("GameNotFoundException"), message = s"game not found with code : $code")

  case class PrizeIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = Some("PrizeNotFoundException"), message = s"prizes not found with id : $id")

  case class NotAuthorizedException(id: UUID, override val message: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, message = message)

  case class ParticipationNotOpenedException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = Some("ParticipationNotOpenedException"), message = s"game with code : $code is not open")

  case class ParticipationCloseException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = Some("ParticipationClosedException"), message = s"game with code : $code is finished")

  val healthCheckRoute: Route =
    path("health") {
      get {
        complete(HttpResponse(StatusCodes.OK, entity = """{"code": 200}"""))
      }
    }
}

trait HttpSupport extends DefaultJsonFormats with Directives with CorsSupport {

  implicit val errorResponse: RootJsonFormat[ErrorResponse] = jsonFormat4(ErrorResponse)


  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {

    case e: FunctionalException =>
      complete(e.statusCode, ErrorResponse(code = e.statusCode.intValue(), `type` = e.getClass.getSimpleName, message = Some(e.message)))

    case _: ArithmeticException =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "1Bad numbers, bad result!!!"))
      }

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
    .handleAll[AuthenticationFailedRejection] { _ =>
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


  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders(origin: String): Directive0 = {
    respondWithHeaders(List(
      Some(origin).filterNot(_ == "*").map(`Access-Control-Allow-Origin`(_)).getOrElse(`Access-Control-Allow-Origin`.*),
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With")
    ))
  }

  //this handles preflight OPTIONS requests.
  private val preflightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
  }

  def extractOriginHeader: HttpHeader => Option[String] = {
    case HttpHeader("origin", value) => Some(value)
    case _ => None
  }

  // Wrap the Route with this method to enable adding of CORS headers
  def corsHandler(r: Route): Route = (headerValueByName("origin") | provide("*")) { origin =>
    addAccessControlHeaders(origin)(preflightRequestHandler ~ r)
  }
}