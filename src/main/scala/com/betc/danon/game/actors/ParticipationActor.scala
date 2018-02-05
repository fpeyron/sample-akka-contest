package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.ParticipationActor.{ParticipateCmd, ParticipationEvent}
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.models.ParticipationDto.{ParticipateResponse, ParticipationStatusType}
import com.betc.danon.game.models.PrizeDao.PrizeResponse

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object ParticipationActor {

  def props(id: UUID)(implicit repository: Repository, materializer: ActorMaterializer) = Props(new ParticipationActor(id))

  def name(id: UUID) = s"participation-$id"

  // Command
  sealed trait Cmd

  // Event
  sealed trait Event

  case class ParticipateCmd(country_code: String, game_code: String, customerId: String) extends Cmd

  case class ParticipationEvent(participationId: UUID, participationDate: Instant, gameId: UUID, customerId: String, instantwin: Option[InstantwinExtended] = None) extends Event

}

class ParticipationActor(gameId: UUID)(implicit val repository: Repository, val materializer: ActorMaterializer) extends PersistentActor with ActorLogging {

  var lastInstantWin: Option[InstantwinExtended] = None
  var nextInstantWins: List[InstantwinExtended] = List.empty[InstantwinExtended]
  var gameIsFinished: Boolean = false

  override def receiveCommand: Receive = {

    case ClusterSingletonActor.GameLinesEvent =>
      refreshInstantWins()


    case cmd: ParticipateCmd => try {

      val originalSender = sender
      val now = Instant.now()

      persist {
        ParticipationEvent(
          participationId = UUID.randomUUID(),
          participationDate = now,
          gameId = gameId,
          customerId = cmd.customerId,
          instantwin = getInstantWin(now)
        )
      } { event =>

        // Return response
        originalSender ! ParticipateResponse(
          id = event.participationId,
          date = event.participationDate,
          status = event.instantwin.map(_ => ParticipationStatusType.Win).getOrElse(ParticipationStatusType.Lost),
          prize = event.instantwin.map(p => new PrizeResponse(p.prize))
        )

        // Refresh instantWins list
        if (event.instantwin.isDefined) {
          whichRefreshInstantWins()
        }
      }
    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

  }

  private def getInstantWin(instant: Instant): Option[InstantwinExtended] = {
    val instantWin = nextInstantWins.find(_.activate_date.isBefore(instant))

    log.debug(s"lastInstantWin: \t${lastInstantWin.map(_.id)}")
    log.debug(s"instantWin: \t${instantWin.map(_.id)}")
    log.debug(s"nextInstantWins: \t${nextInstantWins.map(t => t.id + " : " + t.activate_date).take(2).mkString("\t")}")

    if (instantWin.isDefined) {
      lastInstantWin = instantWin
      nextInstantWins = nextInstantWins.filterNot(_.id == instantWin.get.id)
    }
    instantWin
  }

  private def whichRefreshInstantWins(): Unit = {
    if (nextInstantWins.isEmpty && !gameIsFinished) {
      refreshInstantWins()
    }
  }

  override def receiveRecover: Receive = {
    case event: ParticipationEvent if event.instantwin.isDefined =>
      lastInstantWin = event.instantwin

    case RecoveryCompleted =>
      refreshInstantWins()
  }

  private def refreshInstantWins(): Unit = {
    nextInstantWins = Await.result(
      repository.instantwin.fetchWithPrizeBy(gameId)
        .filter(r => lastInstantWin.forall(l => (r.id.compareTo(l.id) > 0 && r.activate_date == l.activate_date) || r.activate_date.isAfter(l.activate_date)))
        .take(10).runWith(Sink.collection)
      , Duration.Inf)
    if (nextInstantWins.isEmpty)
      gameIsFinished = true
  }

  override def persistenceId: String = s"GAME-$gameId"
}
