package com.betc.danon.game.routes

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.util.Timeout
import com.betc.danon.game.Config
import com.betc.danon.game.actors.GameParticipationActor.ParticipateCmd
import com.betc.danon.game.actors.GamesActor.GameFindQuery
import com.betc.danon.game.models.ParticipationDto.{CustomerGameResponse, CustomerParticipateRequest, CustomerParticipateResponse, PartnerJsonSupport}
import com.betc.danon.game.utils.HttpSupport.ErrorResponse
import com.betc.danon.game.utils.{CorsSupport, DefaultJsonSupport}
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
  extends Directives with DefaultJsonSupport with PartnerJsonSupport with CorsSupport {

  import akka.pattern.ask

  implicit val ec: ExecutionContext
  private implicit val timeout: Timeout = Config.Api.timeout
  implicit val clusterSingletonProxy: ActorRef

  def partnerRoute: Route = pathPrefix("partner") {
    corsHandler(partner_customer_participate ~ partner_customer_findGames)
  }


  /**
    *
    * @return customer.participate
    */
  @Path("{country_code}/customers/{customer_id}/participations")
  @ApiOperation(value = "participate to game", notes = "", nickname = "partner.customer.participate", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of participation", response = classOf[CustomerParticipateResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Participate to game", required = true, dataTypeClass = classOf[CustomerParticipateRequest], paramType = "body")
  ))
  def partner_customer_participate: Route = path(Segment / "customers" / Segment / "participations") { (country_code, customer_id) =>
    post {
      entity(as[CustomerParticipateRequest]) { request =>
        onSuccess(clusterSingletonProxy ?
          ParticipateCmd(
            country_code = country_code.toUpperCase,
            game_code = request.game_code,
            customer_id.toUpperCase,
            transaction_code = request.transaction_code,
            metadata = request.metadata.getOrElse(Map.empty),
            ean = request.ean
          )) {
          case response: CustomerParticipateResponse => complete(StatusCodes.OK, response)
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
    new ApiResponse(code = 200, message = "Return result list of Games", responseContainer = "list", response = classOf[CustomerGameResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "tags", value = "tags", required = false, dataType = "string", paramType = "query")
  ))
  def partner_customer_findGames: Route = path(Segment / "customers" / Segment / "games") { (country_code, customer_id) =>
    get {
      parameters('tags.?, 'codes.?) { (tagsOptional, codesOptional) =>
        onSuccess(clusterSingletonProxy ? GameFindQuery(
          country_code = country_code.toUpperCase,
          games = codesOptional.map(_.split(",").toSeq).getOrElse(Seq.empty),
          tags = tagsOptional.map(_.toUpperCase.split(",").toSeq).getOrElse(Seq.empty),
          customer_id = Some(customer_id.toUpperCase()))
        ) {
          case response: Seq[CustomerGameResponse] => complete(StatusCodes.OK, response)
        }
      }
    }
  }
}

