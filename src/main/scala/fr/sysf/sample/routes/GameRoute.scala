package fr.sysf.sample.routes

import java.io.File
import javax.ws.rs.Path

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import fr.sysf.sample.DefaultJsonFormats
import fr.sysf.sample.actors.GameActor._
import fr.sysf.sample.models.GameDomain._
import fr.sysf.sample.models.InstantwinDomain.Instantwin
import fr.sysf.sample.routes.AuthentifierSupport.UserContext
import io.swagger.annotations._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext


/**
  *
  */
@Api(value = "/games", produces = javax.ws.rs.core.MediaType.APPLICATION_JSON, authorizations = Array(
  new Authorization(value = "basicAuth", scopes = Array(
    new AuthorizationScope(scope = "read:games", description = "read your games for your country"),
    new AuthorizationScope(scope = "write:games", description = "modify games for your country")
  ))
))
@Path("/games")
trait GameRoute
  extends Directives with DefaultJsonFormats with GameJsonFormats {

  import akka.pattern.ask

  implicit val ec: ExecutionContext
  private implicit val timeout: Timeout = Timeout(2.seconds)
  implicit val gameActor: ActorRef

  def gameRoute: Route = AuthentifierSupport.asAuthentified { implicit uc: UserContext =>
      game_getAll ~ game_get ~ game_create ~ game_update ~ game_delete ~ game_activate ~ game_archive ~
      game_getLines ~ game_createLine ~ game_deleteLine ~ game_updateLine ~
      game_downloadInstantwins
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
  @ApiOperation(value = "list games by criteria", notes = "", nickname = "game.getAll", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of games", responseContainer = "Seq", response = classOf[GameForListResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def game_getAll(implicit uc: UserContext): Route = path("games") {
    get {
      parameters('type.?, 'status.?) { (typesOptional, statusOptional) =>
        complete {
          (gameActor ? GameListRequest(uc = uc, types = typesOptional, status = statusOptional)).mapTo[Seq[GameForListResponse]]
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
    new ApiResponse(code = 404, message = "Game is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_get(implicit uc: UserContext): Route = path("games" / JavaUUID) { id =>
    get {
      onSuccess(gameActor ? GameGetRequest(uc, id)) {
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
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "game to create", required = true, dataTypeClass = classOf[GameCreateRequest], paramType = "body")
  ))
  def game_create(implicit uc: UserContext): Route = path("games") {
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
    new ApiResponse(code = 404, message = "Game is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "game to update", required = true, dataTypeClass = classOf[GameUpdateRequest], paramType = "body")
  ))
  def game_update(implicit uc: UserContext): Route = path("games" / JavaUUID) { id =>
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
    new ApiResponse(code = 404, message = "Game is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to create", required = true, dataType = "string", paramType = "path")
  ))
  def game_delete(implicit uc: UserContext): Route = path("games" / JavaUUID) { id =>
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
    new ApiResponse(code = 404, message = "Game is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to activate", required = true, dataType = "string", paramType = "path")
  ))
  def game_activate(implicit uc: UserContext): Route = path("games" / JavaUUID / "action-activate") { id =>
    delete {
      onSuccess(gameActor ? GameActivateCmd(uc, id)) {
        case None => complete(StatusCodes.OK)
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
    new ApiResponse(code = 200, message = "Return when game is archived"),
    new ApiResponse(code = 404, message = "Game is not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game to archive", required = true, dataType = "string", paramType = "path")
  ))
  def game_archive(implicit uc: UserContext): Route = path("games" / JavaUUID / "action-archive") { id =>
    delete {
      onSuccess(gameActor ? GameArchiveCmd(uc, id)) {
        case None => complete(StatusCodes.OK)
      }
    }
  }


  /**
    * -------------------------------
    * GameLine
    * -------------------------------
    */


  /**
    * game.getLines
    *
    * @return Seq[GameLineResponse]
    */
  @Path("/{id}/lines")
  @ApiOperation(value = "list of line for game", notes = "", nickname = "game.getLines", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of game lines", responseContainer = "Seq", response = classOf[GameLineResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_getLines(implicit uc: UserContext): Route = path("games" / JavaUUID / "lines") { id =>
    get {
      complete {
        (gameActor ? GameLineListRequest(uc, id)).mapTo[Seq[GameLineResponse]]
      }
    }
  }


  /**
    * game.createLine
    *
    * @return GameLineResponse
    */
  @Path("/{id}/lines")
  @ApiOperation(value = "create line for game", notes = "", nickname = "game.createLine", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game line created", response = classOf[GameLineResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_createLine(implicit uc: UserContext): Route = path("games" / JavaUUID / "lines") { id =>
    post {
      entity(as[GameLineCreateRequest]) { request =>
        onSuccess(gameActor ? GameLineCreateCmd(uc, id, request)) {
          case response: GameLineResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    * game.updateLine
    *
    * @return GameLineResponse
    */
  @Path("/{id}/lines/{lineId}")
  @ApiOperation(value = "create line for game", notes = "", nickname = "game.updateLine", httpMethod = "PUT")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return game line updated", response = classOf[GameLineResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "lineId", value = "id of game line", required = true, dataType = "string", paramType = "path")
  ))
  def game_updateLine(implicit uc: UserContext): Route = path("games" / JavaUUID / "lines" / JavaUUID) { (id, lineId) =>
    put {
      entity(as[GameLineCreateRequest]) { request =>
        onSuccess(gameActor ? GameLineUpdateCmd(uc, id, lineId, request)) {
          case response: GameLineResponse => complete(StatusCodes.OK, response)
        }
      }
    }
  }


  /**
    * game.deleteLine
    *
    * @return Void
    */
  @Path("/{id}/lines/{lineId}")
  @ApiOperation(value = "create line for game", notes = "", nickname = "game.deleteLine", httpMethod = "DELETE")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return when game line is deleted", response = classOf[Void]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "lineId", value = "id of game line", required = true, dataType = "string", paramType = "path")
  ))
  def game_deleteLine(implicit uc: UserContext): Route = path("games" / JavaUUID / "lines" / JavaUUID) { (id, lineId) =>
    delete {
      onSuccess(gameActor ? GameLineDeleteCmd(uc, id, lineId)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }



  /**
    * game.downloadInstantwins
    *
    * @return File
    */
  @Path("/{id}/instantwins")
  @ApiOperation(value = "Download instantwins for game", notes = "", nickname = "game.downloadInstantwins", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return file game", response = classOf[File]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "id of game", required = true, dataType = "string", paramType = "path")
  ))
  def game_downloadInstantwins(implicit uc: UserContext): Route = path("games" / JavaUUID / "instantwins") { id =>
    get {
      onSuccess((gameActor ? GameGetInstantwinRequest(uc, id)).mapTo[List[Instantwin]]) { response =>
        val mapStream =
          Source.single("activate_date\tattribution_date\tgame_id\n")
            .concat(Source(response).map((t: Instantwin) => s"${t.activateDate}\t${t.attributionDate}\t${t.game_id}\n"))
            .map(ByteString.apply)
        complete {
          HttpEntity(contentType = ContentTypes.`text/csv(UTF-8)`, data = mapStream)
        }
      }
    }
  }

}