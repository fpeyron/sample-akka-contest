package com.betc.danon.game.routes

import java.io.File
import javax.ws.rs.Path

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import com.betc.danon.game.Config
import com.betc.danon.game.actors.BoGameActor._
import com.betc.danon.game.models.GameDto._
import com.betc.danon.game.models.GameEntity.GamePrize
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.utils.AuthenticateSupport.UserContext
import com.betc.danon.game.utils.HttpSupport.ErrorResponse
import com.betc.danon.game.utils.{AuthenticateSupport, CorsSupport, DefaultJsonSupport}
import io.swagger.annotations._

import scala.concurrent.ExecutionContext


/**
  *
  */
@Api(value = "Game", produces = javax.ws.rs.core.MediaType.APPLICATION_JSON, authorizations = Array(
  new Authorization(value = "basicAuth", scopes = Array(
    new AuthorizationScope(scope = "read:games", description = "read your games for your country"),
    new AuthorizationScope(scope = "write:games", description = "modify games for your country")
  ))
))
@Path("/bo/games")
trait BoGameRoute
  extends Directives with DefaultJsonSupport with GameJsonSupport with CorsSupport {

  import akka.pattern.ask

  implicit val ec: ExecutionContext
  implicit val materializer: ActorMaterializer
  private implicit val timeout: Timeout = Config.Api.timeout
  implicit val gameActor: ActorRef

  def gameRoute: Route = pathPrefix("bo" / "games") {
    corsHandler(AuthenticateSupport.asAuthenticated { implicit uc: UserContext =>
      game_findBy ~ game_get ~ game_create ~ game_update ~ game_delete ~ game_activate ~ game_archive ~
        game_getPrizes ~ game_addPrize ~ game_deletePrize ~ game_updatePrize ~
        game_downloadInstantwins
    })
  }

  /**
    * -------------------------------
    * Game
    * -------------------------------
    */


  /**
    *
    * @return Seq[GameResponse]
    */
  @ApiOperation(value = "list games by criteria", notes = "", nickname = "game.findBy", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of games", responseContainer = "list", response = classOf[GameForListDto]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "type", value = "types of game", required = false, dataType = "string", paramType = "query"),
    new ApiImplicitParam(name = "status", value = "status of game", required = false, dataType = "string", paramType = "query")
  ))
  def game_findBy(implicit @ApiParam(hidden = true) uc: UserContext): Route = pathEndOrSingleSlash {
    get {
      parameters('type.?, 'status.?) { (typesOptional, statusOptional) =>
        complete {
          (gameActor ? GameListQuery(uc = uc, types = typesOptional, status = statusOptional)).mapTo[Source[GameForListDto, NotUsed]]
        }
      }
    }
  }


  /**
    *
    * @return GameResponse
    */
  @Path("/{id}")
  @ApiOperation(value = "get game", notes = "", nickname = "game.get", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game", response = classOf[GameResponse]),
    new ApiResponse(code = 404, message = "Game is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_get(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    get {
      onSuccess(gameActor ? GameGetQuery(uc, id)) {
        case response: GameResponse => complete(StatusCodes.OK, response)
      }
    }
  }


  /**
    *
    * @return GameResponse
    */
  @ApiOperation(value = "create game", notes = "", nickname = "game.create", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game created", response = classOf[GameResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "game to create", required = true, dataTypeClass = classOf[GameCreateRequest], paramType = "body")
  ))
  def game_create(implicit @ApiParam(hidden = true) uc: UserContext): Route = pathEndOrSingleSlash {
    post {
      entity(as[GameCreateRequest]) { request =>
        onSuccess(gameActor ? GameCreateCmd(uc, request)) {
          case response: GameResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return GameResponse
    */
  @Path("/{id}")
  @ApiOperation(value = "update game", notes = "", nickname = "game.update", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game", response = classOf[GameResponse]),
    new ApiResponse(code = 404, message = "Game is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "game to update", required = true, dataTypeClass = classOf[GameUpdateRequest], paramType = "body")
  ))
  def game_update(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    put {
      entity(as[GameUpdateRequest]) { request =>
        onSuccess(gameActor ? GameUpdateCmd(uc, id, request)) {
          case response: GameResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    *
    * @return None
    */
  @Path("/{id}")
  @ApiOperation(value = "delete game", notes = "", nickname = "game.delete", httpMethod = "DELETE")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when game is deleted", response = classOf[Void]),
    new ApiResponse(code = 404, message = "Game is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to create", required = true, dataType = "string", paramType = "path")
  ))
  def game_delete(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID) { id =>
    delete {
      onSuccess(gameActor ? GameDeleteCmd(uc, id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }


  /**
    *
    * @return None
    */
  @Path("/{id}/action-activate")
  @ApiOperation(value = "activate game", notes = "", nickname = "game.activate", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when game is activated"),
    new ApiResponse(code = 404, message = "Game is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to activate", required = true, dataType = "string", paramType = "path")
  ))
  def game_activate(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "action-activate") { id =>
    put {
      onSuccess(gameActor ? GameActivateCmd(uc, id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }


  /**
    *
    * @return None
    */
  @Path("/{id}/action-archive")
  @ApiOperation(value = "activate game", notes = "", nickname = "game.archive", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when game is archived", response = classOf[ErrorResponse]),
    new ApiResponse(code = 404, message = "Game is not found", response = classOf[ErrorResponse]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to archive", required = true, dataType = "string", paramType = "path")
  ))
  def game_archive(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "action-archive") { id =>
    put {
      onSuccess(gameActor ? GameArchiveCmd(uc, id)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }


  /**
    * -------------------------------
    * GamePrize
    * -------------------------------
    */


  /**
    * game.getPrizes
    *
    * @return Seq[GamePrizeResponse]
    */
  @Path("/{id}/prizes")
  @ApiOperation(value = "list of prize for game", notes = "", nickname = "game.getPrizes", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of game prizeq", responseContainer = "Seq", response = classOf[GamePrize]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_getPrizes(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "prizes") { id =>
    get {
      complete {
        (gameActor ? GameListPrizesQuery(uc, id)).mapTo[Seq[GamePrize]]
      }
    }
  }


  /**
    * game.addPrize
    *
    * @return GamePrizeResponse
    */
  @Path("/{id}/prizes")
  @ApiOperation(value = "add prize for game", notes = "", nickname = "game.addPrize", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game prize created", response = classOf[GamePrize]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_addPrize(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "prizes") { id =>
    post {
      entity(as[GamePrizeCreateRequest]) { request =>
        onSuccess(gameActor ? GameAddPrizeCmd(uc, id, request)) {
          case response: GamePrize => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    * game.updatePrize
    *
    * @return GamePrizeResponse
    */
  @Path("/{id}/prizes/{prizeId}")
  @ApiOperation(value = "create prize for game", notes = "", nickname = "game.updatePrize", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game prize updated", response = classOf[GamePrize]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "prizeId", value = "id of game prize", required = true, dataType = "string", paramType = "path")
  ))
  def game_updatePrize(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "prizes" / JavaUUID) { (id, prizeId) =>
    put {
      entity(as[GamePrizeCreateRequest]) { request =>
        onSuccess(gameActor ? GameUpdatePrizeCmd(uc, id, prizeId, request)) {
          case response: GamePrize => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    * game.deletePrize
    *
    * @return Void
    */
  @Path("/{id}/prizes/{prizeId}")
  @ApiOperation(value = "Delete prize for game", notes = "", nickname = "game.deletePrize", httpMethod = "DELETE")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when game prize is deleted", response = classOf[Void]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "prizeId", value = "id of game prize", required = true, dataType = "string", paramType = "path")
  ))
  def game_deletePrize(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "prizes" / JavaUUID) { (id, prizeId) =>
    delete {
      onSuccess(gameActor ? GameRemovePrizeCmd(uc, id, prizeId)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }


  /**
    * -------------------------------
    * InstantWin
    * -------------------------------
    */


  /**
    * game.downloadInstantwins
    *
    * @return File
    */
  @Path("/{id}/instantwins")
  @ApiOperation(value = "Download instantwins for game", notes = "", nickname = "game.downloadInstantwins", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return file game", response = classOf[File]),
    new ApiResponse(code = 500, message = "Internal server error", response = classOf[ErrorResponse])
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_downloadInstantwins(implicit @ApiParam(hidden = true) uc: UserContext): Route = path(JavaUUID / "instantwins") { id =>
    get {
      onSuccess(gameActor ? GameGetInstantwinQuery(uc, id)) {
        case source: Source[_, _] => complete {
          val mapStream = Source.single("activate_date\tattribution_date\tgameprize_id\tprize_id\tprize_type\tprize_label\tprize_vendor_code\tprize_face_value\n")
            .concat(source.map(_.asInstanceOf[InstantwinExtended]).map(t => s"${t.activate_date}\t${t.gameprize_id}\t${t.gameprize_id}\t${t.prize.id}\t${t.prize.`type`.toString}\t${t.prize.label}\t${t.prize.vendor_code.getOrElse("")}\t${t.prize.face_value.getOrElse("")}\n"))


            .map(ByteString.apply)
          HttpEntity(contentType = ContentTypes.`text/csv(UTF-8)`, data = mapStream)
        }
      }
    }
  }

}