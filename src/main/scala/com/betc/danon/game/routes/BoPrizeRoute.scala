package com.betc.danon.game.routes

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.betc.danon.game.actors.BoPrizeActor._
import com.betc.danon.game.models.PrizeDao.{PrizeCreateRequest, PrizeJsonSupport, PrizeResponse}
import com.betc.danon.game.models.PrizeDomain.Prize
import com.betc.danon.game.utils.AuthenticateSupport.UserContext
import com.betc.danon.game.utils.HttpSupport.ErrorResponse
import com.betc.danon.game.utils.{AuthenticateSupport, CorsSupport, DefaultJsonSupport}
import com.betc.danon.game.{Config, RouteContext}
import io.swagger.annotations._

import scala.concurrent.ExecutionContext

/**
  *
  */
@Api(value = "Prize", produces = MediaType.APPLICATION_JSON, authorizations = Array(
  new Authorization(value = "basicAuth", scopes = Array(
    new AuthorizationScope(scope = "read:prizes", description = "read your prizes for your country"),
    new AuthorizationScope(scope = "write:prizes", description = "modify prizes for your country")
  ))
))
@Path("/bo/prizes")
trait BoPrizeRoute
  extends Directives with DefaultJsonSupport with PrizeJsonSupport with CorsSupport {

  import akka.pattern.ask

  implicit val context: RouteContext
  implicit val ec: ExecutionContext
  private implicit val timeout: Timeout = Config.Api.timeout

  def prizeRoute: Route = pathPrefix("bo" / "prizes") {
    corsHandler(AuthenticateSupport.asAuthenticated { implicit uc: UserContext =>
      prize_getAll ~ prize_get ~ prize_create ~ prize_update ~ prize_delete
    })
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
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "game_id", value = "id of game", required = false, dataType = "string", paramType = "query")
  ))
  def prize_getAll(implicit @ApiParam(hidden = true) uc: UserContext): Route = pathEndOrSingleSlash {
    get {
      parameters('game_id.?) { (gameIdOptional) =>
        complete {
          (context.boPrizeActor ? PrizeListQuery(uc, gameIdOptional)).mapTo[Source[PrizeResponse, NotUsed]]
        }
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
  def prize_get(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    get {
      onSuccess(context.boPrizeActor ? PrizeGetQuery(uc, id)) {
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
  def prize_create(implicit @ApiParam(hidden = true) uc: UserContext): Route = pathEndOrSingleSlash {
    post {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(context.boPrizeActor ? PrizeCreateCmd(uc, request)) {
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
    new ApiResponse(code = 200, message = "Return prize", response = classOf[Prize]),
    new ApiResponse(code = 404, message = "Prize is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of prize", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "prize to update", required = true, dataTypeClass = classOf[PrizeCreateRequest], paramType = "body")
  ))
  def prize_update(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    put {
      entity(as[PrizeCreateRequest]) { request =>
        onSuccess(context.boPrizeActor ? PrizeUpdateCmd(uc, id, request)) {
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
  def prize_delete(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    delete {
      onSuccess(context.boPrizeActor ? PrizeDeleteCmd(uc, id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }
}

