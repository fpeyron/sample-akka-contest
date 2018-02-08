package com.betc.danon.game.actors

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor.{CustomerParticipateCmd, CustomerParticipationState}
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent
import com.betc.danon.game.models.Event
import com.betc.danon.game.models.GameEntity.{Game, GameLimit, GameLimitType, GameLimitUnit, GameStatusType}
import com.betc.danon.game.models.ParticipationDto.{CustomerParticipateResponse, ParticipationStatusType}
import com.betc.danon.game.models.PrizeDao.PrizeResponse
import com.betc.danon.game.utils.HttpSupport._

import scala.concurrent.Await
import scala.concurrent.duration._


object CustomerWorkerActor {

  def props(id: String)(implicit repository: Repository, materializer: ActorMaterializer) = Props(new CustomerWorkerActor(id))

  def name(id: String) = s"customer-$id"


  // Cmd
  sealed trait CustomerCmd

  // Event
  sealed trait CustomerEvent extends Event


  case class CustomerParticipateCmd(
                                     country_code: String,
                                     customerId: String,
                                     transaction_code: Option[String],
                                     ean: Option[String],
                                     metadata: Map[String, String],
                                     game: Game
                                   ) extends CustomerCmd


  case class CustomerParticipationState(game_id: UUID, participationDate: Instant, participationStatus: ParticipationStatusType.Value)

}

class CustomerWorkerActor(customerId: String)(implicit val repository: Repository, val materializer: ActorMaterializer) extends PersistentActor with ActorLogging {

  var participations: Seq[CustomerParticipationState] = Seq.empty[CustomerParticipationState]

  override def receiveRecover: Receive = {

    case event: GameParticipationEvent =>
      participations = participations :+ CustomerParticipationState(game_id = event.gameId, participationDate = event.timestamp, participationStatus = event.instantwin.map(_ => ParticipationStatusType.Lost).getOrElse(ParticipationStatusType.Lost))

    case RecoveryCompleted =>
  }


  override def receiveCommand: Receive = {

    case cmd: CustomerParticipateCmd => try {

      log.info(s"$customerId : participate to ${cmd.game.id}")

      // check Status
      if (cmd.game.status != GameStatusType.Activated) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active start_date
      if (cmd.game.start_date.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active end_date
      if (cmd.game.end_date.isBefore(Instant.now)) {
        throw ParticipationCloseException(code = cmd.game.code)
      }

      // check Dependencies
      val participationDependenciesInFail = getParticipationDependenciesInFail(game = cmd.game)
      if (participationDependenciesInFail.nonEmpty) {
        throw ParticipationDependenciesException(code = cmd.game.code)
      }

      // check Limits
      val participationLimitsInFail = getParticipationLimitsInFail(game = cmd.game)
      if (participationLimitsInFail.exists(_.`type` == GameLimitType.Participation)) {
        throw ParticipationLimitException(code = cmd.game.code)
      }

      // Return Lost if some GameLimit is reached
      (if (participationLimitsInFail.exists(_.`type` == GameLimitType.Win)) {
        GameParticipationEvent(
          timestamp = Instant.now,
          participationId = UUID.randomUUID(),
          gameId = cmd.game.id,
          countryCode = cmd.country_code,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          metadata = cmd.metadata
        )
      }
      else {
        implicit val timeout: Timeout = Timeout(1.minutes)
        Await.result(getOrCreateGameWorkerActor(cmd.game.id) ? GameWorkerActor.GamePlayCmd(
          country_code = cmd.country_code,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          metadata = cmd.metadata
        ), Duration.Inf)
      }) match {

        case event: GameParticipationEvent =>
          //val originalSender = sender
          persistAsync(event) { _ =>
            // Return response
            sender() ! CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = event.instantwin.map(_ => ParticipationStatusType.Win).getOrElse(ParticipationStatusType.Lost),
              prize = event.instantwin.map(p => new PrizeResponse(p.prize))
            )
          }
          // Refresh state list
          participations = participations :+ CustomerParticipationState(game_id = event.gameId, participationDate = event.timestamp, participationStatus = event.instantwin.map(_ => ParticipationStatusType.Lost).getOrElse(ParticipationStatusType.Lost))

        case _ => sender() ! _
      }
    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }
  }

  override def persistenceId: String = s"CUSTOMER-$customerId"


  private def getOrCreateGameWorkerActor(id: UUID): ActorRef = context.child(GameWorkerActor.name(id))
    .getOrElse(context.actorOf(GameWorkerActor.props(id), GameWorkerActor.name(id)))


  private def getParticipationDependenciesInFail(game: Game): Seq[UUID] = {
    game.parents.filterNot(parent => participations exists (_.game_id == parent))
  }

  private def getParticipationLimitsInFail(game: Game): Seq[GameLimit] = game.limits.filter { limit =>
    limit.unit match {
      case GameLimitUnit.Game =>
        participations.count(_.game_id == game.id) >= limit.value
      case GameLimitUnit.Second =>
        val limitDate = Instant.now().minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.game_id == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
      case GameLimitUnit.Day =>
        val limitDate = Instant.now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.game_id == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
    }
  }
}
