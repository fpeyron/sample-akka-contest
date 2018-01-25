package fr.sysf.sample.actors

import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import fr.sysf.sample.actors.GameActor._
import fr.sysf.sample.actors.InstantwinActor.{InstanwinCreateCmd, InstanwinDeleteCmd, InstanwinUpdateCmd}
import fr.sysf.sample.models.GameDto._
import fr.sysf.sample.models.GameEntity.{Game, GameLimit, GamePrize}
import fr.sysf.sample.routes.AuthentifierSupport.UserContext
import fr.sysf.sample.routes.HttpSupport.{EntityNotFoundException, InvalidInputException, NotAuthorizedException}


object GameActor {

  def props = Props(new GameActor)

  val Name = "games-singleton"

  // Query
  sealed trait Query
  case class GameListQuery(uc: UserContext, types: Option[String], status: Option[String]) extends Query
  case class GameGetQuery(uc: UserContext, id: UUID) extends Query
  case class GameGetInstantwinQuery(uc: UserContext, game_id: UUID) extends Query
  case class GamePrizeListQuery(uc: UserContext, gameId: UUID) extends Query
  case class GameEanListQuery(uc: UserContext, gameId: UUID) extends Query

  // Command
  sealed trait Cmd
  case class GameCreateCmd(uc: UserContext, gameCreateRequest: GameCreateRequest) extends Cmd
  case class GameUpdateCmd(uc: UserContext, id: UUID, gameUpdateRequest: GameUpdateRequest) extends Cmd
  case class GameDeleteCmd(uc: UserContext, id: UUID) extends Cmd
  case class GameActivateCmd(uc: UserContext, id: UUID) extends Cmd
  case class GameArchiveCmd(uc: UserContext, id: UUID) extends Cmd
  case class GamePrizeCreateCmd(uc: UserContext, id: UUID, request: GamePrizeCreateRequest) extends Cmd
  case class GamePrizeUpdateCmd(uc: UserContext, id: UUID, prizeId: UUID, request: GamePrizeCreateRequest) extends Cmd
  case class GamePrizeDeleteCmd(uc: UserContext, id: UUID, prizeId: UUID) extends Cmd
  case class GameEanCreateCmd(uc: UserContext, id: UUID, request: Seq[String]) extends Cmd
  case class GameEanAddCmd(uc: UserContext, id: UUID, ean: String) extends Cmd
  case class GameEanDeleteCmd(uc: UserContext, id: UUID, ean: String) extends Cmd
}


class GameActor extends Actor with ActorLogging {

  var state = Seq.empty[Game]


  override def receive: Receive = {

    case GameListQuery(uc, types, status) => try {
      val restrictedTypes = types.map(_.split(",").flatMap(GameType.withNameOptional))
      val restrictedStatus = status.map(_.split(",").flatMap(GameStatusType.withNameOptional))
      sender ! state.filter(c => uc.country_code == c.country_code &&  restrictedStatus.forall(_.contains(c.status)) && restrictedTypes.forall(_.contains(c.`type`)))
        .sortBy(c => c.start_date)
        .map(r => GameForListResponse(
          id = r.id,
          `type` = r.`type`,
          status = r.status,
          parent_id = r.parent_id,
          reference = r.reference,
          title = r.title,
          start_date = r.start_date,
          timezone = r.timezone,
          end_date = r.end_date,
          input_type = r.input_type,
          input_point = r.input_point
        ))

    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameGetQuery(uc, id) => try {

      // check existing game
      val gameResponse = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! gameResponse.map(r => GameResponse(
        id = r.id,
        `type` = r.`type`,
        status = r.status,
        parent_id = r.parent_id,
        reference = r.reference,
        title = r.title,
        start_date = r.start_date,
        timezone = r.timezone,
        end_date = r.end_date,
        input_type = r.input_type,
        input_point = r.input_point,
        limits = r.limits,
        prizes = r.prizes
      )).get

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case cmd: GameGetInstantwinQuery =>
      // check existing game
      val gameResponse = state.find(c => c.id == cmd.game_id && c.country_code == cmd.uc.country_code)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = cmd.game_id)
      }

      getInstantwinActor(cmd.game_id) forward cmd


    case GameCreateCmd(uc, request) => try {

      // Validation input
      val request_error = checkGameInputForCreation(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Persist
      val newId = UUID.randomUUID
      val game = Game(
        id = newId,
        `type` = request.`type`.map(GameType.withName) getOrElse GameType.Instant,
        status = GameStatusType.Draft,
        reference = request.reference.getOrElse(newId.toString),
        country_code = uc.country_code,
        title = request.title,
        start_date = request.start_date.getOrElse(Instant.now),
        end_date = request.end_date.getOrElse(Instant.now),
        timezone = request.timezone.map(ZoneId.of(_).toString).getOrElse("UTC"),
        parent_id = request.parent_id,
        input_type = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        input_point = request.input_point,
        limits = request.limits.getOrElse(Seq.empty)
          .map(f => GameLimit(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Session),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1)
          ))
      )
      state = state :+ game

      // Return response
      sender ! GameResponse(
        id = game.id,
        `type` = game.`type`,
        status = game.status,
        parent_id = game.parent_id,
        reference = game.reference,
        title = game.title,
        start_date = game.start_date,
        timezone = game.timezone,
        end_date = game.end_date,
        input_type = game.input_type,
        input_point = game.input_point,
        limits = game.limits,
        prizes = game.prizes
      )

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameUpdateCmd(uc, id, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
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
      val game = Game(
        id = entity.get.id,
        `type` = entity.get.`type`,
        status = entity.get.status,
        reference = request.reference.getOrElse(entity.get.reference),
        country_code = entity.get.country_code,
        title = request.title.map(Some(_)).getOrElse(entity.get.title),
        start_date = request.start_date.getOrElse(entity.get.start_date),
        end_date = request.end_date.getOrElse(entity.get.end_date),
        timezone = request.timezone.getOrElse("UTC"),
        parent_id = request.parent_id,
        input_type = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        input_point = request.input_point.orElse(entity.get.input_point),
        limits = request.limits.map(
          _.map(f => GameLimit(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Session),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1)
          ))).getOrElse(entity.get.limits)
      )
      state = state.filterNot(_.id == game.id) :+ game
      sender ! GameResponse(
        id = game.id,
        `type` = game.`type`,
        status = game.status,
        parent_id = game.parent_id,
        reference = game.reference,
        title = game.title,
        start_date = game.start_date,
        timezone = game.timezone,
        end_date = game.end_date,
        input_type = game.input_type,
        input_point = game.input_point,
        limits = game.limits,
        prizes = game.prizes
      )

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameDeleteCmd(uc, id) => try {

      // check existing game
      val game = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status != GameStatusType.Draft) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (state.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      // Persist
      state = state.filterNot(_.id == id)

      // Delete instantwins
      //forwardToInstantwinActor(InstanwinDeleteCmd())


      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GameActivateCmd(uc, id) => try {

      // check existing game
      val game = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status != GameStatusType.Draft) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (state.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      state = state.filterNot(_.id == game.get.id) :+ game.get.copy(status = GameStatusType.Activated)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GameArchiveCmd(uc, id) => try {

      // check existing game
      val game = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (game.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (game.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // check dependency
      if (state.exists(_.parent_id == id)) {
        throw NotAuthorizedException(id = id, message = Some("HAS_DEPENDENCIES"))
      }

      state = state.filterNot(_.id == game.get.id) :+ game.get.copy(status = GameStatusType.Archived)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


    case GamePrizeListQuery(uc, id) => try {

      // check existing game
      val gameResponse = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! gameResponse.get.prizes

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GamePrizeCreateCmd(uc, id, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
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
      val gamePrize = GamePrize(
        id = newId,
        prize_id = request.prize_id.get,
        start_date = request.start_date.getOrElse(entity.get.start_date),
        end_date = request.end_date.getOrElse(entity.get.end_date),
        quantity = request.quantity.getOrElse(1)
      )
      state = state.filterNot(_.id == id) :+ entity.get.copy(prizes = entity.get.prizes :+ gamePrize)

      // Generate instantwins
      getInstantwinActor(id) ! InstanwinCreateCmd(gamePrize)

      // Return response
      sender ! gamePrize

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GamePrizeUpdateCmd(uc, id, prizeId, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check existing game
      val entityPrize = entity.get.prizes.find(c => c.id == prizeId)
      if (entityPrize.isEmpty) {
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
      val gamePrize = GamePrize(
        id = entityPrize.get.id,
        prize_id = request.prize_id.getOrElse(entityPrize.get.prize_id),
        start_date = request.start_date.getOrElse(entityPrize.get.start_date),
        end_date = request.end_date.getOrElse(entityPrize.get.end_date),
        quantity = request.quantity.getOrElse(entityPrize.get.quantity)
      )

      state = state.filterNot(_.id == id) :+ entity.get.copy(prizes = entity.get.prizes :+ gamePrize)

      // Regenerate instantwins
      getInstantwinActor(id) ! InstanwinUpdateCmd(gamePrize)

      // Return response
      sender ! gamePrize

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GamePrizeDeleteCmd(uc, id, prizeId) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }


      // check existing game
      val entityPrize = entity.get.prizes.find(c => c.id == prizeId)
      if (entityPrize.isEmpty) {
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
      state = state.filterNot(_.id == id) :+ entity.get.copy(prizes = entity.get.prizes.filter(_.id == prizeId))

      // Delete instantwins
      getInstantwinActor(id) ! InstanwinDeleteCmd(Some(prizeId))

      // Return response
      sender ! None

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameEanListQuery(uc, id) => try {

      // check existing game
      val gameResponse = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (gameResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! gameResponse.get.input_eans.getOrElse(Seq.empty)

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameEanCreateCmd(uc, id, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Persist
      state = state.filterNot(_.id == id) ++ state.filter(_.id == id).map(_.copy(input_eans = Some(request)))

      // Return response
      sender ! None

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameEanAddCmd(uc, id, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Persist
      state = state.filterNot(_.id == id) ++ state.filter(_.id == id).map(l => l.copy(input_eans = Some(l.input_eans.getOrElse(Seq.empty) :+ request)))

      // Return response
      sender ! None

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case GameEanDeleteCmd(uc, id, request) => try {

      // check existing game
      val entity = state.find(c => c.id == id && c.country_code == uc.country_code)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      // check status
      if (entity.get.status == GameStatusType.Archived) {
        throw NotAuthorizedException(id = id, message = Some("NOT_AUTHORIZED_STATUS"))
      }

      // Persist
      state = state.filterNot(_.id == id) ++ state.filter(_.id == id).map(l => l.copy(input_eans = l.input_eans.filterNot(_ == request)))

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
      if (request.reference.exists(r => state.exists(_.reference == r)))
        ("reference", s"ALREADY_EXISTS : already exists with same reference") else null
    ) ++ Option(
      if (request.parent_id.isDefined && state.exists(_.id == request.parent_id.get))
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

  private def checkGameInputForUpdate(id: UUID, request: GameUpdateRequest): Iterable[(String, String)] = {
    var index = 0
    //Validation input
    Option(
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
      if (request.reference.isDefined && state.exists(s => s.reference == request.reference.get && s.id != id))
        ("reference", s"ALREADY_EXISTS : already exists with same reference") else null
    ) ++ Option(
      if (request.parent_id.isDefined && state.exists(_.id == request.parent_id.get))
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

  def getInstantwinActor(game_id: UUID): ActorRef = context.child(InstantwinActor.name(game_id)).getOrElse(createInstantwinActor(game_id))

  def forwardToInstantwinActor: Actor.Receive = {
    case cmd: GameGetInstantwinQuery =>
      context.child(InstantwinActor.name(cmd.game_id)).fold(createAndForward(cmd, cmd.game_id))(forwardCommand(cmd))
  }

  def forwardCommand(cmd: Any)(shopper: ActorRef): Unit = shopper forward cmd

  def createAndForward(cmd: Any, game_id: UUID): Unit = createInstantwinActor(game_id) forward cmd

  def createInstantwinActor(game_id: UUID): ActorRef = context.actorOf(InstantwinActor.props(game_id), InstantwinActor.name(game_id))
}