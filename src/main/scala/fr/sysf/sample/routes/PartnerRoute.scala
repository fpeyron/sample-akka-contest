package fr.sysf.sample.routes

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.util.Timeout
import fr.sysf.sample.Config
import fr.sysf.sample.actors.ParticipationActor.ParticipateCmd
import fr.sysf.sample.models.ParticipationDto.{ParticipateRequest, ParticipateResponse, PartnerJsonFormats}
import fr.sysf.sample.routes.HttpSupport.ErrorResponse
import io.swagger.annotations._

import scala.concurrent.ExecutionContext

/**
  *
  */
@Api(value = "/partner", produces = MediaType.APPLICATION_JSON, authorizations = Array(
  new Authorization(value = "basicAuth", scopes = Array(
    new AuthorizationScope(scope = "read:partner", description = "read active games"),
    new AuthorizationScope(scope = "write:partner", description = "create participation games")
  ))
))
@Path("/partner")
trait PartnerRoute
  extends Directives with DefaultJsonFormats with PartnerJsonFormats {

  import akka.pattern.ask

  implicit val ec: ExecutionContext
  private implicit val timeout: Timeout = Config.Api.timeout
  implicit val clusterSingletonProxy: ActorRef

  def partnerRoute: Route = pathPrefix("partner") {
    partner_customer_participate ~ partner_game_find
  }


  /**
    *
    * @return customer.participate
    */
  @Path("{country_code}/customers/{customer_id}/participations")
  @ApiOperation(value = "participate to game", notes = "", nickname = "partner.customer.participate", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of participation", response = classOf[ParticipateResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "Participate to game", required = true, dataTypeClass = classOf[ParticipateRequest], paramType = "body")
  ))
  def partner_customer_participate: Route = path(Segment / "customers" / Segment / "participations") { (country_code, customer_id) =>
    post {
      entity(as[ParticipateRequest]) { request =>
        onSuccess(clusterSingletonProxy ? ParticipateCmd(country_code = country_code.toUpperCase, game_code = request.game_code.get, customer_id.toUpperCase)) {
          case response: ParticipateResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return customer.findGames
    */
  @Path("{country_code}/customers/{customer_id}/games")
  @ApiOperation(value = "find games with customer context", notes = "", nickname = "partner.customer.findGames", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result list of Games", response = classOf[ParticipateResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "tags", value = "tags", required = false, dataType = "string", paramType = "query")
  ))
  def partner_customer_findGames: Route = path(Segment / "customers" / Segment / "games") { (country_code, customer_id) =>
    get {
      complete(StatusCodes.OK)
    }
  }


  /**
    *
    * @return customer.getGame
    */
  @Path("{country_code}/customers/{customer_id}/games/{game_code}")
  @ApiOperation(value = "find games with customer context", notes = "", nickname = "partner.participate", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of participation", response = classOf[ParticipateResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "tags", value = "tags", required = false, dataType = "string", paramType = "query")
  ))
  def partner_game_find: Route = path(Segment / "customers" / Segment / "games") { (country_code, customer_id) =>
    get {
      complete(StatusCodes.OK)
    }
  }
}

