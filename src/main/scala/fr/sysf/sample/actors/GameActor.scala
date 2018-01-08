package fr.sysf.sample.actors

import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import fr.sysf.sample.DefaultDirectives.{EntityNotFoundException, InvalidInputException, NotAuthorizedException}
import fr.sysf.sample.actors.GameActor._
import fr.sysf.sample.models.Game._


object GameActor {

  // Command
  sealed trait Cmd

  case class GameCreateCmd(gameCreateRequest: GameCreateRequest)

  case class GameUpdateCmd(id: UUID, gameUpdateRequest: GameCreateRequest) extends Cmd

  case class GameDeleteCmd(id: UUID) extends Cmd

  case class GameActivateCmd(id: UUID) extends Cmd

  case class GameArchiveCmd(id: UUID) extends Cmd

  case class GameLineCreateCmd(id: UUID, request: GameLineCreateRequest) extends Cmd

  case class GameLineUpdateCmd(id: UUID, lineId: UUID, request: GameLineCreateRequest) extends Cmd

  case class GameLineDeleteCmd(id: UUID, lineId: UUID) extends Cmd

}


class GameActor extends Actor with ActorLogging {

  var gameState = Seq.empty[GameResponse]
  var gameInstantState = Seq.empty


  def receive: Receive = {

    case GameListRequest(types, status) => try {
      val restrictedTypes = types.map(_.split(",").flatMap(GameType.withNameOptional))
      val restrictedStatus = status.map(_.split(",").flatMap(GameStatusType.withNameOptional))
      sender ! gameState.filter(c => restrictedStatus.forall(_.contains(c.status)) && restrictedTypes.forall(_.contains(c.`type`)))
        .sortBy(c => c.start_date)

    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameGetRequest(id) => try {

      // check existing game
      val gameResponse = gameState.find(c => c.id == id)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! gameResponse.get

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameCreateCmd(request) => try {

      // Validation input
      val request_error = checkGameInputForCreation(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Persist
      val newId = UUID.randomUUID
      val game = GameResponse(
        id = newId,
        `type` = request.`type`.map(GameType.withName) getOrElse GameType.Instant,
        status = GameStatusType.Draft,
        reference = request.reference.getOrElse(newId.toString),
        country_code = request.country_code.map(_.toUpperCase).getOrElse("CA"),
        portal_code = request.portal_code,
        title = request.title,
        start_date = request.start_date.getOrElse(Instant.now),
        end_date = request.end_date.getOrElse(Instant.now),
        timezone = request.timezone.map(ZoneId.of).map(_.toString).getOrElse("UTC"),
        parent_id = request.parent_id,
        input_type = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        input_point = request.input_point,
        input_eans = request.input_eans,
        input_freecodes = request.input_freecodes,
        limits = request.limits.getOrElse(Seq.empty)
          .map(f => GameLimitResponse(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Session),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1)
          ))
      )
      gameState = gameState :+ game

      // Return response
      sender ! game

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameUpdateCmd(id, request) => try {

      // check existing game
      val entity = gameState.find(c => c.id == id)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status != GameStatusType.Draft) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Check input payload
      val request_error = checkGameInputForUpdate(id, request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Persist updrade
      val gameUpdated = GameResponse(
        id = entity.get.id,
        `type` = entity.get.`type`,
        status = entity.get.status,
        reference = request.reference.getOrElse(entity.get.reference),
        country_code = entity.get.country_code,
        portal_code = request.portal_code.map(Some(_)).getOrElse(entity.get.portal_code),
        title = request.title.map(Some(_)).getOrElse(entity.get.title),
        start_date = request.start_date.getOrElse(entity.get.start_date),
        end_date = request.end_date.getOrElse(entity.get.end_date),
        timezone = request.timezone.getOrElse("UTC"),
        parent_id = request.parent_id,
        input_type = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        input_point = request.input_point.orElse(entity.get.input_point),
        input_eans = request.input_eans.orElse(entity.get.input_eans),
        input_freecodes = request.input_freecodes.orElse(entity.get.input_freecodes),
        limits = request.limits.map(
          _.map(f => GameLimitResponse(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Session),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1)
          ))).getOrElse(entity.get.limits)
      )
      gameState = gameState.filterNot(_.id == gameUpdated.id) :+ gameUpdated
      sender ! gameUpdated

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameDeleteCmd(id) => try {

      // check existing game
      val game = gameState.find(c => c.id == id)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status != GameStatusType.Draft) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (gameState.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      gameState = gameState.filterNot(_.id == id)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GameActivateCmd(id) => try {

      // check existing game
      val game = gameState.find(c => c.id == id)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status != GameStatusType.Draft) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (gameState.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      gameState = gameState.filterNot(_.id == game.get.id) :+ game.get.copy(status = GameStatusType.Activated)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GameArchiveCmd(id) => try {

      // check existing game
      val game = gameState.find(c => c.id == id)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status != GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (gameState.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      gameState = gameState.filterNot(_.id == game.get.id) :+ game.get.copy(status = GameStatusType.Archived)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GameLineListRequest(id) => try {

      // check existing game
      val gameResponse = gameState.find(c => c.id == id)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! gameResponse.get.lines

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameLineCreateCmd(id, request) => try {

      // check existing game
      val entity = gameState.find(c => c.id == id)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Validation input
      //val request_error = checkGameInputForCreation(request)
      //if (request_error.nonEmpty) {
      //  throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      //}

      // Persist
      val newId = UUID.randomUUID
      val gameLine = GameLineResponse(
        id = newId,
        prize_id = request.prize_id.get,
        start_date = request.start_date.getOrElse(entity.get.start_date),
        end_date = request.end_date.getOrElse(entity.get.end_date),
        quantity = request.quantity.getOrElse(1)
      )

      gameState = gameState.filterNot(_.id == id) :+ entity.get.copy(lines = entity.get.lines :+ gameLine)

      // Return response
      sender ! gameLine

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameLineUpdateCmd(id, lineId, request) => try {

      // check existing game
      val entity = gameState.find(c => c.id == id)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check existing game
      val entityLine = entity.get.lines.find(c => c.id == lineId)
      if (entityLine.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Validation input
      //val request_error = checkGameInputForCreation(request)
      //if (request_error.nonEmpty) {
      //  throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      //}

      // Persist
      val gameLine = GameLineResponse(
        id = entityLine.get.id,
        prize_id = request.prize_id.getOrElse(entityLine.get.prize_id),
        start_date = request.start_date.getOrElse(entityLine.get.start_date),
        end_date = request.end_date.getOrElse(entityLine.get.end_date),
        quantity = request.quantity.getOrElse(entityLine.get.quantity)
      )

      gameState = gameState.filterNot(_.id == id) :+ entity.get.copy(lines = entity.get.lines :+ gameLine)

      // Return response
      sender ! gameLine

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameLineDeleteCmd(id, lineId) => try {

      // check existing game
      val entity = gameState.find(c => c.id == id)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check existing game
      val entityLine = entity.find(c => c.id == lineId)
      if (entityLine.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Validation input
      //val request_error = checkGameInputForCreation(request)
      //if (request_error.nonEmpty) {
      //  throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      //}

      // Persist
      gameState = gameState.filterNot(_.id == id) :+ entity.get.copy(lines = entity.get.lines.filter(_.id == lineId))

      // Return response
      sender ! None

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

  }

  private def checkGameInputForCreation(request: GameCreateRequest): Iterable[(String, String)] = {
    var index = 0
    //Validation input
    Option(
      if (request.`type`.isEmpty)
        ("type", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.`type`.isDefined && !GameType.values.map(_.toString).contains(request.`type`.get))
        ("type", s"UNKNOWN_VALUE : list of values : ${GameType.all.mkString(",")}") else null
    ) ++ Option(
      if (request.reference.exists(_.length < 2))
        ("reference", s"INVALID_VALUE : should have more 2 chars") else null
    ) ++ Option(
      if (request.country_code.isEmpty)
        ("country_code", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.country_code.exists(_.length != 2))
        ("country_code", s"INVALID_VALUE : should have 2 chars") else null
    ) ++ Option(
      if (request.start_date.isEmpty)
        ("start_date", s"INVALID_VALUE : should be in the future") else null
    ) ++ Option(
      if (request.start_date.isDefined && request.start_date.get.isBefore(Instant.now))
        ("start_date", s"INVALID_VALUE : should be is the future") else null
    ) ++ Option(
      if (request.end_date.isDefined && request.start_date.isDefined && request.end_date.get.isBefore(request.start_date.get))
        ("end_date", s"INVALID_VALUE : should be after start_date") else null
    ) ++ Option(
      if (request.timezone.isDefined && !request.timezone.exists(isTimezone))
        ("timezone", s"INVALID_VALUE : timezone value is invalid") else null
    ) ++ Option(
      if (request.end_date.isEmpty)
        ("end_date", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.reference.exists(r => gameState.exists(_.reference == r)))
        ("reference", s"ALREADY_EXISTS : already exists with same reference") else null
    ) ++ Option(
      if (request.parent_id.isDefined && gameState.exists(_.id == request.parent_id.get))
        ("parent_id", s"ENTITY_NOT_FOUND : should already exists") else null
    ) ++ Option(
      if (request.title.isDefined && request.title.get.length > 80)
        ("title", s"INVALID_VALUE : should have max 80 characters") else null
    ) ++ Option(
      if (request.input_type.isDefined && !GameInputType.values.map(_.toString).contains(request.input_type.get))
        ("input_type", s"UNKNOWN_VALUE : list of values : ${GameInputType.all.mkString(",")}") else null
    ) ++ Option(
      if (request.input_type.getOrElse(GameInputType.Other) == GameInputType.Point && request.input_point.isEmpty)
        ("input_point", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.input_point.exists(_ < 1))
        ("input_point", s"INVALID_VALUE : input_point should be > 0") else null
    ) ++ request.limits.getOrElse(Seq.empty).flatMap(f => {
      index += 1
      checkGameLimitInput(index, f)
    })
  }

  private def checkGameInputForUpdate(id: UUID, request: GameCreateRequest): Iterable[(String, String)] = {
    var index = 0
    //Validation input
    Option(
      if (request.`type`.isDefined && !GameType.values.map(_.toString).contains(request.`type`.get))
        ("type", s"UNKNOWN_VALUE : list of values : ${
          GameType.all.mkString(",")
        }") else null
    ) ++ Option(
      if (request.reference.exists(_.length < 2))
        ("reference", s"INVALID_VALUE : should have more 2 chars") else null
    ) ++ Option(
      if (request.start_date.isDefined && request.start_date.get.isBefore(Instant.now))
        ("start_date", s"INVALID_VALUE : should be in the future") else null
    ) ++ Option(
      if (request.end_date.isDefined && request.start_date.isDefined && request.end_date.get.isBefore(request.start_date.get))
        ("end_date", s"INVALID_VALUE : should be after start_date") else null
    ) ++ Option(
      if (request.timezone.isDefined && !request.timezone.exists(isTimezone))
        ("timezone", s"INVALID_VALUE : timezone value is invalid") else null
    ) ++ Option(
      if (request.reference.isDefined && gameState.exists(s => s.reference == request.reference.get && s.id != id))
        ("reference", s"ALREADY_EXISTS : already exists with same reference") else null
    ) ++ Option(
      if (request.parent_id.isDefined && gameState.exists(_.id == request.parent_id.get))
        ("parent_id", s"ENTITY_NOT_FOUND : should already exists") else null
    ) ++ Option(
      if (request.title.isDefined && request.title.get.length > 80)
        ("title", s"INVALID_VALUE : should have max 80 characters") else null
    ) ++ Option(
      if (request.input_type.isDefined && !GameInputType.values.map(_.toString).contains(request.input_type.get))
        ("input_type", s"UNKNOWN_VALUE : list of values : ${GameInputType.all.mkString(",")}") else null
    ) ++ Option(
      if (request.input_point.exists(_ < 1))
        ("input_point", s"INVALID_VALUE : input_point should be > 0") else null
    ) ++ request.limits.getOrElse(Seq.empty).flatMap(f => {
      index += 1
      checkGameLimitInput(index, f)
    })
  }

  private def checkGameLimitInput(index: Int, requestLimit: GameLimitRequest): Iterable[(String, String)] = {
    Option(
      if (requestLimit.`type`.isEmpty)
        (s"limit.$index.limit_type", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (requestLimit.`type`.isDefined && requestLimit.`type`.map(GameLimitType.withName).isEmpty)
        (s"limit.$index.limit_type", s"UNKNOWN_VALUE : list of values : ${
          GameLimitType.all.mkString(",")
        }") else null
    ) ++ Option(
      if (requestLimit.unit.isEmpty)
        (s"limit.$index.unit", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (requestLimit.unit.isDefined && requestLimit.unit.map(GameLimitUnit.withName).isEmpty)
        (s"limit.$index.unit", s"UNKNOWN_VALUE : list of values : ${
          GameLimitUnit.all.mkString(",")
        }") else null
    ) ++ Option(
      if (requestLimit.unit.isDefined && requestLimit.unit.get != GameLimitUnit.Session.toString && requestLimit.unit_value.exists(_ < 1))
        (s"limit.$index.unit_value", s"INVALID_VALUE : value > 0") else null
    ) ++ Option(
      if (requestLimit.unit.isDefined && requestLimit.unit.get != GameLimitUnit.Session.toString && requestLimit.value.exists(_ < 1))
        (s"limit.$index.value", s"INVALID_VALUE : value and > 0") else null
    )
  }

  private def isTimezone(value: String) = try {
    ZoneId.of(value)
    true
  }
  catch {
    case _: java.time.zone.ZoneRulesException => false
  }
}