package com.betc.danon.game.actors

import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.BoGameActor._
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipated
import com.betc.danon.game.models.GameDto._
import com.betc.danon.game.models.GameEntity._
import com.betc.danon.game.models.InstantwinDomain.Instantwin
import com.betc.danon.game.repositories.GameExtension
import com.betc.danon.game.utils.AuthenticateSupport.UserContext
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.{ActorUtil, JournalReader}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object BoGameActor {

  val Name = "games-singleton"

  def props(implicit repository: Repository, materializer: ActorMaterializer, clusterSingletonProxy: ActorRef, journalReader: JournalReader) = Props(new BoGameActor)

  // Query
  sealed trait Query

  case class GameListQuery(uc: UserContext, types: Option[String], status: Option[String]) extends Query

  case class GameGetQuery(uc: UserContext, id: UUID) extends Query

  case class GameListPrizesQuery(uc: UserContext, id: UUID) extends Query

  case class GameGetInstantwinQuery(uc: UserContext, id: UUID) extends Query

  case class GameGetParticipationsQuery(uc: UserContext, id: UUID, customerIdOptional: Option[String] = None) extends Query


  // Command
  sealed trait Cmd

  case class GameCreateCmd(uc: UserContext, gameCreateRequest: GameCreateRequest) extends Cmd

  case class GameUpdateCmd(uc: UserContext, id: UUID, gameUpdateRequest: GameUpdateRequest) extends Cmd

  case class GameDeleteCmd(uc: UserContext, id: UUID) extends Cmd

  case class GameActivateCmd(uc: UserContext, id: UUID) extends Cmd

  case class GameArchiveCmd(uc: UserContext, id: UUID) extends Cmd

  case class GameAddPrizeCmd(uc: UserContext, id: UUID, request: GamePrizeCreateRequest) extends Cmd

  case class GameUpdatePrizeCmd(uc: UserContext, id: UUID, gamePrizeId: UUID, request: GamePrizeCreateRequest) extends Cmd

  case class GameRemovePrizeCmd(uc: UserContext, id: UUID, gamePrizeId: UUID) extends Cmd

}


class BoGameActor(implicit val repository: Repository, val materializer: ActorMaterializer, val clusterSingletonProxy: ActorRef, val journalReader: JournalReader) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {

    case GameListQuery(uc, types, status) => try {

      val restrictedTypes = types.map(_.split(",").flatMap(GameType.withNameOptional).toSeq)
      val restrictedStatus = status.map(_.split(",").flatMap(GameStatus.withNameOptional).toSeq)

      val sourceList: Source[GameForListDto, NotUsed] = repository.game.fetchBy(
        country_code = Some(uc.country_code),
        types = restrictedTypes.getOrElse(Seq.empty[GameType.Value]),
        status = restrictedStatus.getOrElse(Seq.empty[GameStatus.Value])
      )
        .map(new GameForListDto(_))

      sender ! sourceList
    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameGetQuery(uc, id) => try {

      repository.game.getById(id, GameExtension.all).map { game =>
        // check existing game
        if (!game.exists(_.countryCode == uc.country_code)) {
          throw GameIdNotFoundException(id = id)
        } else {
          game.map(new GameResponse(_)).get
        }
      }.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameCreateCmd(uc, request) => try {

      // check existing game
      val request_error = checkGameInputForCreation(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(fields = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Generate Record
      val newId = UUID.randomUUID
      val game = Game(
        id = newId,
        `type` = request.`type`.map(GameType.withName) getOrElse GameType.Instant,
        status = GameStatus.Draft,
        code = request.code.getOrElse(newId.toString),
        countryCode = uc.country_code,
        title = request.title,
        startDate = request.start_date.getOrElse(Instant.now),
        endDate = request.end_date.getOrElse(Instant.now),
        timezone = request.timezone.map(ZoneId.of(_).toString).getOrElse("UTC"),
        inputType = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        inputPoint = request.input_point,
        limits = request.limits.getOrElse(Seq.empty)
          .map(f => GameLimit(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Game),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1),
            parent_id = f.parent_id.flatMap(id => ActorUtil.string2UUID(id))
          )),
        inputEans = request.input_eans.getOrElse(Seq.empty).distinct,
        inputFreecodes = request.input_freecodes.getOrElse(Seq.empty).distinct,
        tags = request.tags.getOrElse(Seq.empty).distinct
      )

      // Check existing code
      if (Await.result(repository.game.findByCode(game.code), Duration.Inf)
        .exists(r => r.countryCode == game.countryCode && r.status != GameStatus.Archived)) {
        throw InvalidInputException(fields = Map("code" -> "GAME_ALREADY_EXISTS : already exists with same code and status ACTIVE"))
      }

      // Check existing parent
      val parentStringIds: Seq[String] = request.limits.getOrElse(Seq.empty[GameLimitRequest]).filter(_.`type` == GameLimitType.Dependency).flatMap(_.parent_id).distinct
      val parentIds: Seq[UUID] = parentStringIds.flatMap(ActorUtil.string2UUID)

      if (parentIds.nonEmpty) {
        val ids = Await.result(repository.game.findByIds(parentIds), Duration.Inf).filter(_.countryCode == uc.country_code).map(_.id.toString)
        val errors = parentStringIds.flatMap(p => if (!ids.contains(p)) Some("parents", s"GAME_NOT_FOUND : parent should already exists : $p") else None)
        if (errors.nonEmpty) {
          var index = -1
          throw InvalidInputException(fields = errors.map { r => index += 1; s"${r._1}.$index" -> r._2 }.toMap)
        }
      }

      // Persist
      Await.result(repository.game.create(game), Duration.Inf)

      // Return response
      sender ! new GameResponse(game)

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameCreateEvent(game = game)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameUpdateCmd(uc, id, request) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id, GameExtension.all), Duration.Inf)

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) {
        throw GameIdNotFoundException(id = id)
      }

      // check status
      if (game.get.status != GameStatus.Draft) {
        throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")
      }

      // Check input payload
      val request_error = checkGameInputForUpdate(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(fields = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Check existing code
      if (request.code.exists(_ != game.get.code) &&
        Await.result(repository.game.findByCode(request.code.get), Duration.Inf)
          .exists(r => r.countryCode == game.get.countryCode && r.status != GameStatus.Archived)) {
        throw InvalidInputException(fields = Map("code" -> "ALREADY_EXISTS : already exists with same code and status ACTIVE"))
      }

      // Check existing parent
      val parentStringIds: Seq[String] = request.limits.getOrElse(Seq.empty[GameLimitRequest]).filter(_.`type` == GameLimitType.Dependency).flatMap(_.parent_id).distinct
      val parentIds: Seq[UUID] = parentStringIds.flatMap(ActorUtil.string2UUID)

      if (parentIds.nonEmpty) {
        val ids = Await.result(repository.game.findByIds(parentIds), Duration.Inf).filter(_.countryCode == uc.country_code).map(_.id.toString)
        val errors = parentStringIds.flatMap(p => if (!ids.contains(p)) Some("parents", s"GAME_NOT_FOUND : parent should already exists : $p") else None)
        if (errors.nonEmpty) {
          var index = -1
          throw InvalidInputException(fields = errors.map { r => index += 1; s"${r._1}.$index" -> r._2 }.toMap)
        }
      }

      // Persist
      val gameToUpdate = Game(
        id = game.get.id,
        `type` = game.get.`type`,
        status = game.get.status,
        code = request.code.getOrElse(game.get.code),
        countryCode = game.get.countryCode,
        title = request.title.map(Some(_)).getOrElse(game.get.title),
        startDate = request.start_date.getOrElse(game.get.startDate),
        endDate = request.end_date.getOrElse(game.get.endDate),
        timezone = request.timezone.getOrElse("UTC"),
        inputType = request.input_type.map(GameInputType.withName).getOrElse(GameInputType.Other),
        inputPoint = request.input_point.orElse(game.get.inputPoint),
        limits = request.limits.map(
          _.map(f => GameLimit(
            `type` = f.`type`.map(GameLimitType.withName).getOrElse(GameLimitType.Participation),
            unit = f.unit.map(GameLimitUnit.withName).getOrElse(GameLimitUnit.Game),
            unit_value = f.unit_value,
            value = f.value.getOrElse(1)
          ))).getOrElse(game.get.limits),
        inputEans = request.input_eans.getOrElse(game.get.inputEans).distinct,
        inputFreecodes = request.input_freecodes.getOrElse(game.get.inputFreecodes).distinct,
        tags = request.tags.getOrElse(game.get.tags).distinct
      )
      Await.result(repository.game.update(gameToUpdate), Duration.Inf)

      // Return response
      sender ! new GameResponse(gameToUpdate)

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = gameToUpdate)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameDeleteCmd(uc, id) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id), Duration.Inf)

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) throw GameIdNotFoundException(id = id)

      // check status
      if (game.get.status != GameStatus.Draft) throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")

      // Delete instantwins
      deleteInstantWins(game.get.id)

      // Persist
      Await.result(repository.game.delete(id), Duration.Inf)

      // Return response
      sender ! None

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameDeleteEvent(id = id)

    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error(e.getMessage, e)
    }


    case GameActivateCmd(uc, id) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id), Duration.Inf)

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) throw GameIdNotFoundException(id = id)

      // check status
      if (game.get.status != GameStatus.Draft) throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")

      // Persist
      Await.result(repository.game.updateStatus(id, GameStatus.Activated), Duration.Inf)

      // Return response
      sender ! None

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = game.get.copy(status = GameStatus.Activated))

    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error(e.getMessage, e)
    }


    case GameArchiveCmd(uc, id) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id), Duration.Inf)

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) throw GameIdNotFoundException(id = id)

      // check status
      if (game.get.status == GameStatus.Archived) throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")

      // Change status
      Await.result(repository.game.updateStatus(id, GameStatus.Archived), Duration.Inf)

      // Return response
      sender ! None

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = game.get.copy(status = GameStatus.Archived))

    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error(e.getMessage, e)
    }


    case GameListPrizesQuery(uc, id) => try {

      repository.game.getById(id, GameExtension.all).map { game =>
        // check existing game
        if (!game.exists(_.countryCode == uc.country_code)) {
          throw GameIdNotFoundException(id = id)
        } else {
          game.get.prizes
        }
      }.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameAddPrizeCmd(uc, id, request) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id, GameExtension.all), Duration.Inf)

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) {
        throw GameIdNotFoundException(id = id)
      }

      // check status
      if (game.get.status == GameStatus.Archived) {
        throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")
      }

      // Check input payload
      val request_error = checkGamePrizeInputForCreation(game.get, request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(fields = request_error.map(v => v._1 -> v._2).toMap)
      }

      if (request.prize_id.isDefined && Await.result(repository.prize.getById(request.prize_id.get), Duration.Inf).isEmpty) {
        throw InvalidInputException(fields = Map("prize_id" -> s"INVALID_VALUE : prize is unknown with id : $request.prize_id"))
      }

      // Persist
      val newId = UUID.randomUUID
      val gamePrize = GamePrize(
        id = newId,
        prize_id = request.prize_id.get,
        start_date = request.start_date.getOrElse(game.get.startDate),
        end_date = request.end_date.getOrElse(game.get.endDate),
        quantity = request.quantity.getOrElse(1)
      )
      Await.result(repository.game.addPrize(id, gamePrize), Duration.Inf)

      // Return response
      sender ! gamePrize

      // to generate instantwins (asynchronous)
      createInstantwins(id, gamePrize)

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = game.get)


    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameUpdatePrizeCmd(uc, id, prizeId, request) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id, GameExtension.all), Duration.Inf)
      val gamePrize: Option[GamePrize] = game.flatMap(_.prizes.find(_.id == prizeId))

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) {
        throw GameIdNotFoundException(id = id)
      }

      // Get existing game
      if (gamePrize.isEmpty) {
        throw GamePrizeIdNotFoundException(id = prizeId)
      }

      // check status
      if (game.get.status == GameStatus.Archived) {
        throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")
      }

      // Check input payload
      val request_error = checkGamePrizeInputForCreation(game.get, request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(fields = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Persist
      val gamePrizeUpdated = GamePrize(
        id = gamePrize.get.id,
        prize_id = request.prize_id.getOrElse(gamePrize.get.prize_id),
        start_date = request.start_date.getOrElse(gamePrize.get.start_date),
        end_date = request.end_date.getOrElse(gamePrize.get.end_date),
        quantity = request.quantity.getOrElse(gamePrize.get.quantity)
      )
      Await.result(repository.game.updatePrize(game.get.id, gamePrizeUpdated), Duration.Inf)

      // Return response
      sender ! gamePrizeUpdated

      // recreate instantWin
      deleteInstantWins(game.get.id, gamePrize.map(_.id))
      createInstantwins(game.get.id, gamePrizeUpdated)

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = game.get)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameRemovePrizeCmd(uc, id, prize_id) => try {

      // Get existing game
      val game = Await.result(repository.game.getById(id, GameExtension.all), Duration.Inf)
      val gamePrize: Option[GamePrize] = game.flatMap(_.prizes.find(_.id == prize_id))

      // check existing game
      if (!game.exists(_.countryCode == uc.country_code)) {
        throw GameIdNotFoundException(id = id)
      }

      // Get existing game
      if (gamePrize.isEmpty) {
        throw GamePrizeIdNotFoundException(id = prize_id)
      }

      // check status
      if (game.get.status == GameStatus.Archived) {
        throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")
      }

      // Check date
      if (gamePrize.get.start_date.isBefore(Instant.now())) {
        throw NotAuthorizedException(id = id, message = "NOT_AUTHORIZED_STATUS")
      }

      // Persist
      Await.result(repository.game.removePrize(game_id = game.get.id, prize_id = gamePrize.get.id), Duration.Inf)

      // delete instantwins
      deleteInstantWins(game.get.id, Some(gamePrize.get.id))

      // return to sender
      sender() ! None

      // Push event
      clusterSingletonProxy ! GameManagerActor.GameUpdateEvent(game = game.get)


    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case GameGetInstantwinQuery(uc, id) => try {

      repository.game.getById(id).map { game =>
        // check existing game
        if (!game.exists(_.countryCode == uc.country_code)) {
          throw GameIdNotFoundException(id = id)
        }
        (game.get, repository.instantwin.fetchWithPrizeBy(game.get.id))
      }.pipeTo(sender)

    }
    catch {
      case e: GameIdNotFoundException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error(e.getMessage, e)
    }


    case GameGetParticipationsQuery(uc, id, customerId) => try {

      repository.game.getById(id).map { game =>
        // check existing game
        if (!game.exists(_.countryCode == uc.country_code)) {
          throw GameIdNotFoundException(id = id)
        }
        (game.get, journalReader.currentEventsByTag(s"GAME-${id.toString}")
          .map(_.event)
          .collect {
            case event: CustomerParticipated => event
          }
          .filter(e => customerId.forall(_.toUpperCase == e.customerId)))
      }.pipeTo(sender)

    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error(e.getMessage, e)
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
      if (request.code.exists(_.length < 2))
        ("code", s"INVALID_VALUE : should have more 2 chars") else null
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

  private def checkGameInputForUpdate(request: GameUpdateRequest): Iterable[(String, String)] = {
    var index = 0
    //Validation input
    Option(
      if (request.code.exists(_.length < 2))
        ("code", s"INVALID_VALUE : should have more 2 chars") else null
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
      if (requestLimit.unit.isDefined && requestLimit.unit.get != GameLimitUnit.Game.toString && requestLimit.unit_value.exists(_ < 1))
        (s"limit.$index.unit_value", s"INVALID_VALUE : value > 0") else null
    ) ++ Option(
      if (requestLimit.unit.isDefined && requestLimit.unit.get != GameLimitUnit.Game.toString && requestLimit.value.exists(_ < 1))
        (s"limit.$index.value", s"INVALID_VALUE : value and > 0") else null
    ) ++ Option(
      if (requestLimit.`type`.contains(GameLimitType.Dependency.toString) && requestLimit.parent_id.isEmpty)
        (s"limit.$index.game_id", s"MANDATORY_VALUE") else null
    )
  }

  private def isTimezone(value: String) = try {
    ZoneId.of(value)
    true
  }
  catch {
    case _: java.time.zone.ZoneRulesException => false
  }

  private def checkGamePrizeInputForCreation(game: Game, request: GamePrizeCreateRequest): Iterable[(String, String)] = {
    //Validation input
    Option(
      if (request.start_date.isDefined && request.start_date.get.isBefore(Instant.now))
        ("start_date", s"INVALID_VALUE : should be is the future") else null
    ) ++ Option(
      if (request.start_date.isDefined && request.start_date.get.isBefore(game.startDate))
        ("start_date", s"INVALID_VALUE : should be after start_date of the game") else null
    ) ++ Option(
      if (request.start_date.isDefined && request.start_date.get.isAfter(game.endDate))
        ("start_date", s"INVALID_VALUE : should be before end_date of the game") else null
    ) ++ Option(
      if (request.end_date.isDefined && request.start_date.isDefined && request.end_date.get.isBefore(request.start_date.get))
        ("end_date", s"INVALID_VALUE : should be after start_date") else null
    ) ++ Option(
      if (request.end_date.isDefined && request.end_date.get.isBefore(game.startDate))
        ("end_date", s"INVALID_VALUE : should be after start_date of the game") else null
    ) ++ Option(
      if (request.end_date.isDefined && request.end_date.get.isAfter(game.endDate))
        ("end_date", s"INVALID_VALUE : should be before end_date of the game") else null
    )
  }


  private def generateInstantWinDates(quantity: Int, start_date: Instant, end_date: Instant): Source[Instant, NotUsed] = {

    val r = scala.util.Random
    val pas = (end_date.toEpochMilli - start_date.toEpochMilli) / quantity

    Source(1 to quantity)
      .map { i => Instant.ofEpochMilli(start_date.toEpochMilli + (i * pas + r.nextFloat * pas).toLong) }
  }

  private def createInstantwins(id: UUID, gameprize: GamePrize): Unit = {

    Await.result(repository.instantwin.insertAsStream(
      generateInstantWinDates(gameprize.quantity, gameprize.start_date, gameprize.end_date)
        .buffer(1000, OverflowStrategy.backpressure)
        .map { date =>
          Instantwin(
            id = UUID.randomUUID(),
            gameId = id,
            gamePrizeId = gameprize.id,
            prizeId = gameprize.prize_id,
            activateDate = date
          )
        }
    ), Duration.Inf)
  }

  private def deleteInstantWins(id: UUID, gameprize_id: Option[UUID] = None): Unit = {

    Await.result(repository.instantwin.deleteBy(game_id = id, gameprize_id), Duration.Inf)
  }
}