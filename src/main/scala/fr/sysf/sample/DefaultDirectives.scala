package fr.sysf.sample

import java.util.UUID

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import fr.sysf.sample.DefaultDirectives.{EntityNotFoundException, ErrorResponse, InvalidInputException, NotAuthorizedException}
import spray.json._


object DefaultDirectives {

  // Error body
  case class ErrorResponse(code: Int, `type`: String, message: Option[String] = None, detail: Option[Map[String, String]] = None)

  // Exception
  case class EntityNotFoundException(id: UUID, message: Option[String] = None) extends RuntimeException

  case class NotAuthorizedException(id: UUID, message: Option[String] = None) extends RuntimeException

  case class InvalidInputException(message: Option[String] = None, detail: Map[String, String]) extends RuntimeException

}

trait DefaultDirectives extends DefaultJsonFormats with Directives with CorsSupport {

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
    .handleAll[MethodRejection] { methodRejections =>
    complete(StatusCodes.MethodNotAllowed, ErrorResponse(code = StatusCodes.MethodNotAllowed.intValue, `type` = "MethodRejection", message = Some(s"Can't do that! Supported: ${methodRejections.map(_.supported.name).mkString(" or ")}!")))
  }
    .result()

}

