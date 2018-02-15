package com.betc.danon.game.routes

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.util.Timeout
import com.betc.danon.game.actors.CustomerWorkerActor
import com.betc.danon.game.actors.CustomerWorkerActor._
import com.betc.danon.game.actors.GameManagerActor.ParticipateCmd
import com.betc.danon.game.models.ParticipationDto.{CustomerConfirmParticipationRequest, CustomerGameResponse, CustomerParticipateRequest, CustomerParticipateResponse, PartnerJsonSupport}
import com.betc.danon.game.utils.HttpSupport.ErrorResponse
import com.betc.danon.game.utils.{CorsSupport, DefaultJsonSupport}
import com.betc.danon.game.{Config, RouteContext}
import io.swagger.annotations._

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

  implicit val context: RouteContext
  implicit val timeout: Timeout = Config.Api.timeout

  def partnerRoute: Route = pathPrefix("partner") {
    corsHandler(
      partner_customer_getGames ~
        partner_customer_participate ~ partner_customer_getParticipations ~
        partner_customer_confirmParticipation ~
        partner_customer_validateParticipation ~ partner_customer_invalidateParticipation ~
        partner_customer_resetGameParticipations
    )
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
        onSuccess(context.clusterSingletonProxy ?
          ParticipateCmd(
            country_code = country_code.toUpperCase,
            game_code = request.game_code,
            customer_id.toUpperCase,
            transaction_code = request.transaction_code,
            meta = request.meta.getOrElse(Map.empty),
            ean = request.ean
          )) {
          case response: CustomerParticipateResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return customer.confirmParticipation
    */
  @Path("{country_code}/customers/{customer_id}/participations/{participation_id}/action-confirm")
  @ApiOperation(value = "Confirm a participation", notes = "", nickname = "partner.customer.confirmParticipation", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of confirmation participation"),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "participation_id", value = "participation ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Metadata free", required = true, dataTypeClass = classOf[CustomerConfirmParticipationRequest], paramType = "body")
  ))
  def partner_customer_confirmParticipation: Route = path(Segment / "customers" / Segment / "participations" / Segment / "action-confirm") { (country_code, customer_id, participation_id) =>
    put {
      entity(as[CustomerConfirmParticipationRequest]) { request =>
        onSuccess(context.customerCluster ?
          CustomerConfirmParticipationCmd(
            countryCode = country_code.toUpperCase,
            customerId = customer_id.toUpperCase,
            participationId = participation_id,
            meta = request.meta.getOrElse(Map.empty)
          )) {
          case None => complete(StatusCodes.OK, None)
        }
      }
    }
  }


  /**
    *
    * @return customer.validateParticipation
    */
  @Path("{country_code}/customers/{customer_id}/participations/{participation_id}/action-validate")
  @ApiOperation(value = "Validate a participation", notes = "", nickname = "partner.customer.validateParticipation", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of confirmation participation"),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "participation_id", value = "participation ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Metadata free", required = true, dataTypeClass = classOf[CustomerConfirmParticipationRequest], paramType = "body")
  ))
  def partner_customer_validateParticipation: Route = path(Segment / "customers" / Segment / "participations" / Segment / "action-validate") { (country_code, customer_id, participation_id) =>
    put {
      entity(as[CustomerConfirmParticipationRequest]) { request =>
        onSuccess(context.customerCluster ?
          CustomerValidateParticipationCmd(
            countryCode = country_code.toUpperCase,
            customerId = customer_id.toUpperCase,
            participationId = participation_id,
            meta = request.meta.getOrElse(Map.empty)
          )) {
          case None => complete(StatusCodes.OK, None)
        }
      }
    }
  }


  /**
    *
    * @return customer.invalidateParticipation
    */
  @Path("{country_code}/customers/{customer_id}/participations/{participation_id}/action-invalidate")
  @ApiOperation(value = "Validate a participation", notes = "", nickname = "partner.customer.invalidateParticipation", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result of unvalidated participation"),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "participation_id", value = "participation ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Metadata free", required = true, dataTypeClass = classOf[CustomerConfirmParticipationRequest], paramType = "body")
  ))
  def partner_customer_invalidateParticipation: Route = path(Segment / "customers" / Segment / "participations" / Segment / "action-invalidate") { (country_code, customer_id, participation_id) =>
    put {
      entity(as[CustomerConfirmParticipationRequest]) { request =>
        onSuccess(context.customerCluster ?
          CustomerInvalidateParticipationCmd(
            countryCode = country_code.toUpperCase,
            customerId = customer_id.toUpperCase,
            participationId = participation_id,
            meta = request.meta.getOrElse(Map.empty)
          )) {
          case None => complete(StatusCodes.OK, None)
        }
      }
    }
  }


  /**
    *
    * @return customer.getGames
    */
  @Path("{country_code}/customers/{customer_id}/games")
  @ApiOperation(value = "get games with customer context", notes = "", nickname = "partner.customer.getGames", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result list of Games", responseContainer = "list", response = classOf[CustomerGameResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "tags", value = "tags", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "codes", value = "codes", required = false, dataType = "string", paramType = "query")
  ))
  def partner_customer_getGames: Route = path(Segment / "customers" / Segment / "games") { (country_code, customer_id) =>
    get {
      parameters('tags.?, 'codes.?) { (tagsOptional, codesOptional) =>
        onSuccess(context.customerCluster ? CustomerGetGamesQry(
          countryCode = country_code.toUpperCase,
          customerId = customer_id.toUpperCase,
          tags = tagsOptional.map(_.split(",").toSeq).getOrElse(Seq.empty),
          codes = codesOptional.map(_.split(",").toSeq).getOrElse(Seq.empty)
        )) {
          case response: Seq[Any] => complete(StatusCodes.OK, response.asInstanceOf[Seq[CustomerGameResponse]])
        }
      }
    }

  }


  /**
    *
    * @return customer.resetGameParticipations
    */
  @Path("{country_code}/customers/{customer_id}/games/{game_code}/action-reset")
  @ApiOperation(value = "reset participations for a game", notes = "", nickname = "partner.customer.resetGameParticipations", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return nothing"),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "game_code", value = "game_code", required = true, dataType = "string", paramType = "path")
  ))
  def partner_customer_resetGameParticipations: Route = path(Segment / "customers" / Segment / "games" / Segment / "action-reset") { (country_code, customer_id, game_code) =>
    put {
        onSuccess(context.customerCluster ? CustomerResetGameParticipationsCmd(
          countryCode = country_code.toUpperCase,
          customerId = customer_id.toUpperCase,
          gameCode = game_code
        )) {
          case None: Any => complete(StatusCodes.OK, None)
        }
    }

  }


  /**
    *
    * @return customer.getParticipations
    */
  @Path("{country_code}/customers/{customer_id}/participations")
  @ApiOperation(value = "get participations with customer context", notes = "", nickname = "partner.customer.getParticipations", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return result list of Games", responseContainer = "list", response = classOf[CustomerParticipateResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "country_code", value = "country code", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "customer_id", value = "customer ID", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "tags", value = "tags", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "codes", value = "codes", required = false, dataType = "string", paramType = "query")
  ))
  def partner_customer_getParticipations: Route = path(Segment / "customers" / Segment / "participations") { (country_code, customer_id) =>
    get {
      parameters('tags.?, 'codes.?) { (tagsOptional, codesOptional) =>
        onSuccess(context.customerCluster ?
          CustomerWorkerActor.CustomerGetParticipationsQry(
            countryCode = country_code.toUpperCase,
            customerId = customer_id.toUpperCase,
            tags = tagsOptional.map(_.split(",").toSeq).getOrElse(Seq.empty),
            codes = codesOptional.map(_.split(",").toSeq).getOrElse(Seq.empty)
          )) {
          case response: Seq[Any] => complete(StatusCodes.OK, response.asInstanceOf[Seq[CustomerParticipateResponse]])
        }
      }
    }
  }

}

