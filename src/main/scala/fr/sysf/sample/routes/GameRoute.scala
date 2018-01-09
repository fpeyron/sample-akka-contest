package fr.sysf.sample.routes

import java.io.File
import java.time.Instant
import java.util.UUID
import javax.ws.rs.Path

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import fr.sysf.sample.DefaultJsonFormats
import fr.sysf.sample.actors.GameActor._
import fr.sysf.sample.models.GameDomain._
import fr.sysf.sample.models.InstantwinDomain.Instantwin
import io.swagger.annotations._


/**
  *
  */
@Api(value = "/games", produces = javax.ws.rs.core.MediaType.APPLICATION_JSON)
@Path("/games")
trait GameRoute
  extends Directives with DefaultJsonFormats with GameJsonFormats  {

  import akka.pattern.ask

  import scala.concurrent.duration._
  implicit val gameActor: ActorRef
  private implicit val timeout: Timeout = Timeout(2.seconds)

  def gameRoute: Route = game_getAll ~ game_get ~ game_create ~ game_update ~ game_delete ~ game_activate ~ game_archive ~
    game_getLines ~ game_createLine ~ game_deleteLine ~ game_updateLine ~
    game_downloadInstantwins


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
    new ApiResponse(code = 200, message = "Return list of games", responseContainer = "Seq", response = classOf[GameResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def game_getAll: Route = path("games") {
    get {
      parameters('type.?, 'status.?) { (typesOptional, statusOptional) =>
        complete {
          (gameActor ? GameListRequest(types = typesOptional, status = statusOptional)).mapTo[Seq[GameResponse]]
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
  def game_get: Route = path("games" / JavaUUID) { id =>
    get {
      onSuccess(gameActor ? GameGetRequest(id)) {
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
  def game_create: Route = path("games") {
    post {
      entity(as[GameCreateRequest]) { request =>
        onSuccess(gameActor ? GameCreateCmd(request)) {
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
    new ApiImplicitParam(name = "body", value = "game to update", required = true, dataTypeClass = classOf[GameCreateRequest], paramType = "body")
  ))
  def game_update: Route = path("games" / JavaUUID) { id =>
    put {
      entity(as[GameCreateRequest]) { request =>
        onSuccess(gameActor ? GameUpdateCmd(id, request)) {
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
  def game_delete: Route = path("games" / JavaUUID) { id =>
    delete {
      onSuccess(gameActor ? GameDeleteCmd(id)) {
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
  def game_activate: Route = path("games" / JavaUUID / "action-activate") { id =>
    delete {
      onSuccess(gameActor ? GameActivateCmd(id)) {
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
  def game_archive: Route = path("games" / JavaUUID / "action-archive") { id =>
    delete {
      onSuccess(gameActor ? GameArchiveCmd(id)) {
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
  def game_getLines: Route = path("games" / JavaUUID / "lines") { id =>
    get {
      complete {
        (gameActor ? GameLineListRequest(id)).mapTo[Seq[GameLineResponse]]
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
  def game_createLine: Route = path("games" / JavaUUID / "lines") { id =>
    post {
      entity(as[GameLineCreateRequest]) { request =>
        onSuccess(gameActor ? GameLineCreateCmd(id, request)) {
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
  def game_updateLine: Route = path("games" / JavaUUID / "lines" / JavaUUID) { (id, lineId) =>
    put {
      entity(as[GameLineCreateRequest]) { request =>
        onSuccess(gameActor ? GameLineUpdateCmd(id, lineId, request)) {
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
  def game_deleteLine: Route = path("games" / JavaUUID / "lines" / JavaUUID) { (id, lineId) =>
    delete {
      onSuccess(gameActor ? GameLineDeleteCmd(id, lineId)) {
        case None => complete(StatusCodes.OK, None)
      }
    }
  }

  def getInstantwins: Source[Instantwin, NotUsed] = Source(for (i <- 0 until 1000) yield Instantwin(
    id = UUID.randomUUID(),
    game_id = UUID.randomUUID(),
    prize_id = UUID.randomUUID(),
    gameLine_id = UUID.randomUUID(),
    activateDate = Instant.now.plusMillis(i * 1000)
  ))


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
  def game_downloadInstantwins: Route = path("games" / JavaUUID / "instantwins") { id =>
    get {
      onSuccess((gameActor ? GameGetInstantwinRequest(id)).mapTo[List[Instantwin]]) { response =>
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