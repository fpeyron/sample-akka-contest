package fr.sysf.sample.services

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.util.Timeout
import fr.sysf.sample.DefaultJsonFormats
import fr.sysf.sample.actors.PrizeActor.{PrizeCreateCmd, PrizeDeleteCmd, PrizeUpdateCmd}
import fr.sysf.sample.models.Prize.{PrizeCreateRequest, PrizeGetRequest, PrizeJsonFormats, PrizeListRequest, PrizeResponse}
import io.swagger.annotations._


/**
  *
  * @param prizeActor PrizeActor
  */
@Api(value = "/prizes", produces = MediaType.APPLICATION_JSON)
@Path("/prizes")
class PrizeService(prizeActor: ActorRef)
  extends Directives with DefaultJsonFormats with PrizeJsonFormats {

  import akka.pattern.ask

  import scala.concurrent.duration._

  implicit val timeout: Timeout = Timeout(2.seconds)


  def route: Route = prize_getAll ~ prize_get ~ prize_create ~ prize_update ~ prize_delete


  /**
    *
    * @return Seq[PrizeResponse]
    */
  @ApiOperation(value = "list all prizes", notes = "", nickname = "prize.getAll", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of prizes", responseContainer = "Seq", response = classOf[PrizeResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def prize_getAll: Route = path("prizes") {
    get {
      complete {
        (prizeActor ? PrizeListRequest).mapTo[Seq[PrizeResponse]]
      }
    }
  }


  /**
    *
    * @return PrizeResponse
    */
  @Path("/{id}")
  @ApiOperation(value = "get prize", notes = "", nickname = "prize.get", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return prize", response = classOf[PrizeResponse]),
    new ApiResponse(code = 404, message = "Prize is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize", required = true, dataType = "string", paramType = "path")
  ))
  def prize_get: Route = path("prizes" / JavaUUID) { id =>
    get {
      onSuccess(prizeActor ? PrizeGetRequest(id)) {
        case response: PrizeResponse => complete(StatusCodes.OK, response)
      }
    }
  }


  /**
    *
    * @return PrizeResponse
    */
  @ApiOperation(value = "create prize", notes = "", nickname = "prize.create", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return prize created", response = classOf[PrizeResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "prize to create", required = true, dataTypeClass = classOf[PrizeCreateRequest], paramType = "body")
  ))
  def prize_create: Route = path("prizes") {
    post {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(prizeActor ? PrizeCreateCmd(request)) {
          case response: PrizeResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return PrizeResponse
    */
  @Path("/{id}")
  @ApiOperation(value = "update prize", notes = "", nickname = "prize.update", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return prize", response = classOf[PrizeResponse]),
    new ApiResponse(code = 404, message = "Prize is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "prize to update", required = true, dataTypeClass = classOf[PrizeCreateRequest], paramType = "body")
  ))
  def prize_update: Route = path("prizes" / JavaUUID) { id =>
    put {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(prizeActor ? PrizeUpdateCmd(id, request)) {
          case response: PrizeResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return None
    */
  @Path("/{id}")
  @ApiOperation(value = "delete prize", notes = "", nickname = "prize.delete", httpMethod = "DELETE")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when prize is delete"),
    new ApiResponse(code = 404, message = "Prize is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize to delete", required = true, dataType = "string", paramType = "path")
  ))
  def prize_delete: Route = path("prizes" / JavaUUID) { id =>
    delete {
      onSuccess(prizeActor ? PrizeDeleteCmd(id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }
}

