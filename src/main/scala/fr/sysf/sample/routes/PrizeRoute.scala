package fr.sysf.sample.routes

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.util.Timeout
import fr.sysf.sample.DefaultJsonFormats
import fr.sysf.sample.actors.PrizeActor.{PrizeCreateCmd, PrizeDeleteCmd, PrizeUpdateCmd}
import fr.sysf.sample.models.PrizeDomain.{PrizeCreateRequest, PrizeGetRequest, PrizeJsonFormats, PrizeListRequest, PrizeResponse}
import fr.sysf.sample.routes.AuthentifierSupport.UserContext
import fr.sysf.sample.routes.HttpSupport.ErrorResponse
import io.swagger.annotations._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  *
  */
@Api(value = "/prizes", produces = MediaType.APPLICATION_JSON, authorizations = Array(
  new Authorization(value = "basicAuth", scopes = Array(
    new AuthorizationScope(scope = "read:prizes", description = "read your prizes for your country"),
    new AuthorizationScope(scope = "write:prizes", description = "modify prizes for your country")
  ))
))
@Path("/prizes")
trait PrizeRoute
  extends Directives with DefaultJsonFormats with PrizeJsonFormats {

  import akka.pattern.ask

  implicit val ec: ExecutionContext
  private implicit val timeout: Timeout = Timeout(2.seconds)
  implicit val prizeActor: ActorRef

  def prizeRoute: Route = AuthentifierSupport.asAuthentified { implicit uc: UserContext =>
    prize_getAll ~ prize_get ~ prize_create ~ prize_update ~ prize_delete
  }


  /**
    *
    * @return Seq[PrizeResponse]
    */
  @ApiOperation(value = "list all prizes", notes = "", nickname = "prize.getAll", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of prizes", responseContainer = "Seq", response = classOf[PrizeResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
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
    new ApiResponse(code = 404, message = "Prize is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize", required = true, dataType = "string", paramType = "path")
  ))
  def prize_get(implicit @ApiParam(hidden = true) uc: UserContext): Route = path("prizes" / JavaUUID) { id =>
    get {
      onSuccess(prizeActor ? PrizeGetRequest(uc, id)) {
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
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "prize to create", required = true, dataTypeClass = classOf[PrizeCreateRequest], paramType = "body")
  ))
  def prize_create(implicit @ApiParam(hidden = true) uc: UserContext): Route = path("prizes") {
    post {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(prizeActor ? PrizeCreateCmd(uc, request)) {
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
    new ApiResponse(code = 404, message = "Prize is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "prize to update", required = true, dataTypeClass = classOf[PrizeCreateRequest], paramType = "body")
  ))
  def prize_update(implicit @ApiParam(hidden = true) uc: UserContext): Route = path("prizes" / JavaUUID) { id =>
    put {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(prizeActor ? PrizeUpdateCmd(uc, id, request)) {
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
    new ApiResponse(code = 200, message = "Return when prize is delete", response = classOf[ErrorResponse]),
    new ApiResponse(code = 404, message = "Prize is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize to delete", required = true, dataType = "string", paramType = "path")
  ))
  def prize_delete(implicit @ApiParam(hidden = true) uc: UserContext): Route = path("prizes" / JavaUUID) { id =>
    delete {
      onSuccess(prizeActor ? PrizeDeleteCmd(uc, id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }
}

