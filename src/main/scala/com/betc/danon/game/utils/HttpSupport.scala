package com.betc.danon.game.utils

import java.util.UUID

import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import com.betc.danon.game.models.ParticipationDto.ParticipationStatus
import com.betc.danon.game.utils.HttpSupport._
import spray.json._


object HttpSupport {

  // Error body
  case class ErrorResponse(code: Int = 500, `type`: String, message: Option[String] = None, detail: Option[Map[String, String]] = None)

  // Exception
  class FunctionalException(val statusCode: StatusCode, val `type`: String, val message: String, val detail: Option[Map[String, String]] = None) extends RuntimeException

  case class InvalidInputException(fields: Map[String, String]) extends FunctionalException(statusCode = StatusCodes.BadRequest, `type` = "InvalidInputException", message = "Invalid input body", detail = Some(fields))

  case class GameIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = "GameNotFoundException", message = s"game not found with id : $id")

  case class GamePrizeIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = "GamePrizeNotFoundException", message = s"gamePrize not found for this game with id : $id")

  case class GameRefNotFoundException(country_code: String, code: String) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = "GameNotFoundException", message = s"game not found with code : $code")

  case class PrizeIdNotFoundException(id: UUID) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = "PrizeNotFoundException", message = s"prizes not found with id : $id")

  case class NotAuthorizedException(id: UUID, override val message: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "NotAuthorizedException", message = message)

  case class ParticipationNotOpenedException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationNotOpenedException", message = s"game with code : $code is not open")

  case class ParticipationCloseException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationClosedException", message = s"game with code : $code is finished")

  case class ParticipationDependenciesException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationDependenciesException", message = s"Participations dependencies fail for game with code : $code")

  case class ParticipationLimitException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationLimitException", message = s"Limit participations is reached from game with code : $code")

  case class ParticipationEanException(code: String) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationEanException", message = s"Ean is not accepted for game with code : $code")

  case class ParticipationNotFoundException(customerId: String, participationId: String) extends FunctionalException(statusCode = StatusCodes.NotFound, `type` = "ParticipationNotFoundException", message = s"participation not found for customer $customerId : $participationId")

  case class ParticipationConfirmException(customerId: String, participationId: String, participationStatus: ParticipationStatus.Value) extends FunctionalException(statusCode = StatusCodes.Forbidden, `type` = "ParticipationConfirmException", message = s"participation couldn't be confirmed : $participationId")

}

trait HttpSupport extends Directives with DefaultJsonSupport {

  implicit val errorResponse: RootJsonFormat[ErrorResponse] = jsonFormat4(ErrorResponse)


  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {

    case e: FunctionalException =>
      complete(e.statusCode, ErrorResponse(code = e.statusCode.intValue(), `type` = e.getClass.getSimpleName, message = Some(e.message), detail = e.detail))

    case _: ArithmeticException =>
      extractUri { uri =>
        println(s"Request to $uri could not be handled normally")
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Bad numbers, bad result!!!"))
      }

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
    .handleAll[MalformedRequestContentRejection] { malformedRequestContentRejection =>
    complete(StatusCodes.BadRequest, ErrorResponse(code = StatusCodes.BadRequest.intValue, `type` = "MalformedRequestContentRejection", message = malformedRequestContentRejection.headOption.map(_.message)))
  }
    .handleAll[Rejection] { rejection =>
    complete(StatusCodes.BadRequest, ErrorResponse(code = StatusCodes.BadRequest.intValue, `type` = "UnknownRejection", message = rejection.headOption.map(_.toString)))
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