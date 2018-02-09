package com.betc.danon.game.actors

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor._
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent
import com.betc.danon.game.models.Event
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GameStatus}
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.models.ParticipationDto.{CustomerGameResponse, CustomerParticipateResponse, ParticipationStatus}
import com.betc.danon.game.models.PrizeDao.PrizeResponse
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.JournalReader

import scala.concurrent.Await
import scala.concurrent.duration._


object CustomerWorkerActor {

  def props(id: String)(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new CustomerWorkerActor(id))

  def name(id: String) = s"customer-$id"


  // Query
  sealed trait CustomerQuery {
    def customerId: String
  }

  case class CustomerGetParticipationQuery(customerId: String, gameIds: Seq[UUID]) extends CustomerQuery

  case class CustomerGetGamesQuery(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerQuery


  // Cmd
  sealed trait CustomerCmd {
    def customerId: String
  }

  case class CustomerReloadCmd(customerId: String) extends CustomerCmd

  case class CustomerGetParticipationsCmd(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerCmd

  case class CustomerParticipateCmd(
                                     countryCode: String,
                                     customerId: String,
                                     transaction_code: Option[String],
                                     ean: Option[String],
                                     meta: Map[String, String],
                                     game: Game
                                   ) extends CustomerCmd


  // Event
  sealed trait CustomerEvent extends Event

  case class CustomerParticipationEvent(
                                         timestamp: Instant = Instant.now,
                                         participationId: UUID,
                                         gameId: UUID,
                                         countryCode: String,
                                         customerId: String,
                                         instantwin: Option[InstantwinExtended] = None,
                                         transaction_code: Option[String] = None,
                                         ean: Option[String] = None,
                                         meta: Map[String, String] = Map.empty
                                       ) extends CustomerEvent {
    def this(r: GameParticipationEvent) = this(timestamp = r.timestamp, participationId = r.participationId, gameId = r.gameId, countryCode = r.countryCode, customerId = r.customerId, instantwin = r.instantwin, transaction_code = r.transaction_code, ean = r.ean, meta = r.meta)
  }

  case class CustomerParticipationState(game_id: UUID, participationDate: Instant, participationStatus: ParticipationStatus.Value)

}

class CustomerWorkerActor(customerId: String)(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends PersistentActor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  var participations: Seq[CustomerParticipationState] = Seq.empty[CustomerParticipationState]

  override def receiveRecover: Receive = {

    case event: CustomerParticipationEvent =>
      participations = participations :+ CustomerParticipationState(game_id = event.gameId, participationDate = event.timestamp, participationStatus = event.instantwin.map(_ => ParticipationStatus.Win).getOrElse(ParticipationStatus.Lost))

    case RecoveryCompleted =>
  }


  override def receiveCommand: Receive = {

    case CustomerGetParticipationQuery(_, gameIds) =>
      sender() ! participations.filter(p => gameIds.contains(p.game_id))


    case CustomerGetParticipationsCmd(countryCode, _, tags, codes) => try {

      val result = for {

        gameIds <- repository.game.findByTagsAndCodes(tags, codes)
          .filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
          .map(_.id)
          .runWith(Sink.seq)

        participations <- {
          journalReader.currentEventsByPersistenceId(s"CUSTOMER-${customerId.toUpperCase}")
            .map(_.event)
            .collect {
              case event: CustomerParticipationEvent => event
            }
            .filter(event => gameIds.contains(event.gameId))
            .map(event => CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = event.instantwin.map(_ => ParticipationStatus.Win).getOrElse(ParticipationStatus.Lost),
              prize = event.instantwin.map(i => new PrizeResponse(i.prize))
            ))
            .runWith(Sink.seq)
        }
      } yield participations

      result.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case CustomerGetGamesQuery(countryCode, _, tags, codes) => try {
      /*
      val result = for {

        games <- repository.game.findByTagsAndCodes(tags, codes).filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated).runWith(Sink.seq)

        participations <- {
          val gameIds = games.map(_.id)
          journalReader.currentEventsByPersistenceId(s"CUSTOMER-${customerId.toUpperCase}")
            .map(_.event)
            .collect {
              case event: CustomerParticipationEvent => (event.gameId, 1, event.instantwin.map(_ => 1).getOrElse(0))
            }
            .filter(event => gameIds.contains(event._1))
            .runFold(Map.empty[UUID, (Int, Int)]) { (current, event) =>
              current.filterNot(_._1 == event._1) + (event._1 -> current.get(event._1).map(c => (c._1 + event._2, c._2 + event._3)).getOrElse((event._2, event._3)))
            }
        }
      } yield (games, participations)

      result.map { result =>
        result._1
          .map(game => CustomerGameResponse(
            `type` = game.`type`,
            code = game.code,
            title = game.title,
            start_date = game.startDate,
            end_date = game.endDate,
            input_type = game.inputType,
            input_point = game.inputPoint,
            parents = Some(game.parents.flatMap(p => result._1.find(_.id == p)).map(_.code)).find(_.nonEmpty),
            participation_count = result._2.get(game.id).map(_._1).getOrElse(0),
            instant_win_count = result._2.get(game.id).map(_._2).getOrElse(0)
          ))
      }.pipeTo(sender)
      */
      repository
        .game
        .findByTagsAndCodes(tags, codes).filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
        .runWith(Sink.seq)
        .map { games =>
          games
            .map(game => CustomerGameResponse(
              `type` = game.`type`,
              code = game.code,
              title = game.title,
              start_date = game.startDate,
              end_date = game.endDate,
              input_type = game.inputType,
              input_point = game.inputPoint,
              parents = Some(game.parents.flatMap(p => games.find(_.id == p)).map(_.code)).find(_.nonEmpty),
              participation_count = participations.count(_.game_id == game.id),
              instant_win_count = participations.count(p => p.game_id == game.id && p.participationStatus == ParticipationStatus.Win)
            ))
        }.pipeTo(sender)


    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case cmd: CustomerParticipateCmd => try {

      log.info(s"$customerId : participate to ${cmd.game.id}")

      // check Status
      if (cmd.game.status != GameStatus.Activated) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active start_date
      if (cmd.game.startDate.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active end_date
      if (cmd.game.endDate.isBefore(Instant.now)) {
        throw ParticipationCloseException(code = cmd.game.code)
      }

      // check Dependencies
      if (hasParticipationDependencies(game = cmd.game)) {
        throw ParticipationDependenciesException(code = cmd.game.code)
      }

      // check Limits
      val participationLimitsInFail = getParticipationLimitsInFail(game = cmd.game)
      if (participationLimitsInFail.exists(_.`type` == GameLimitType.Participation)) {
        throw ParticipationLimitException(code = cmd.game.code)
      }

      // check Ean
      if (cmd.game.inputType == GameInputType.Pincode && !cmd.ean.exists(e => cmd.game.inputEans.contains(e))) {
        throw ParticipationEanException(code = cmd.game.code)
      }

      // Return Lost if some GameLimit is reached
      (if (participationLimitsInFail.exists(_.`type` == GameLimitType.Win)) {
        GameParticipationEvent(
          timestamp = Instant.now,
          participationId = UUID.randomUUID(),
          gameId = cmd.game.id,
          countryCode = cmd.countryCode,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          meta = cmd.meta
        )
      }
      else {
        implicit val timeout: Timeout = Timeout(1.minutes)
        Await.result(getOrCreateGameWorkerActor(cmd.game.id) ? GameWorkerActor.GamePlayCmd(
          country_code = cmd.countryCode,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          meta = cmd.meta
        ), Duration.Inf)
      }) match {

        case event: GameParticipationEvent =>
          //val originalSender = sender
          persistAsync(new CustomerParticipationEvent(event)) { _ =>
            // Return response
            sender() ! CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = event.instantwin.map(_ => ParticipationStatus.Win).getOrElse(ParticipationStatus.Lost),
              prize = event.instantwin.map(p => new PrizeResponse(p.prize))
            )
          }
          // Refresh state list
          participations = participations :+ CustomerParticipationState(game_id = event.gameId, participationDate = event.timestamp, participationStatus = event.instantwin.map(_ => ParticipationStatus.Win).getOrElse(ParticipationStatus.Lost))

        case _ => sender() ! _
      }
    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

  }

  override def persistenceId: String = s"CUSTOMER-${customerId.toUpperCase}"


  private def getOrCreateGameWorkerActor(id: UUID): ActorRef = context.child(GameWorkerActor.name(id))
    .getOrElse(context.actorOf(GameWorkerActor.props(id), GameWorkerActor.name(id)))


  private def hasParticipationDependencies(game: Game): Boolean = !Some(game.parents).filter(_.nonEmpty).forall(_.exists(parent => participations.exists(_.game_id == parent)))


  private def getParticipationLimitsInFail(game: Game): Seq[GameLimit] = game.limits.filter { limit =>
    val now = Instant.now
    limit.unit match {
      case GameLimitUnit.Game if limit.`type` == GameLimitType.Participation =>
        participations.count(p => p.game_id == game.id) >= limit.value
      case GameLimitUnit.Game if limit.`type` == GameLimitType.Win =>
        participations.count(p => p.game_id == game.id && p.participationStatus == ParticipationStatus.Win) >= limit.value

      case GameLimitUnit.Second if limit.`type` == GameLimitType.Participation =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.game_id == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
      case GameLimitUnit.Second if limit.`type` == GameLimitType.Win =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.game_id == game.id && p.participationStatus == ParticipationStatus.Win && p.participationDate.isAfter(limitDate)) >= limit.value

      case GameLimitUnit.Day if limit.`type` == GameLimitType.Participation =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.game_id == game.id && p.participationDate.isAfter(limitDate)) >= limit.value

      case GameLimitUnit.Day if limit.`type` == GameLimitType.Win =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.game_id == game.id && p.participationStatus == ParticipationStatus.Win && p.participationDate.isAfter(limitDate)) >= limit.value
    }
  }
}
