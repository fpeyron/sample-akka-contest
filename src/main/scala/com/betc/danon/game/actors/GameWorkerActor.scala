package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipateCmd
import com.betc.danon.game.actors.GameWorkerActor.{GameParticipateCmd, GameParticipationEvent, GamePlayCmd, Stop}
import com.betc.danon.game.models.Event
import com.betc.danon.game.models.GameEntity.Game
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.repositories.GameExtension

import scala.concurrent.Await
import scala.concurrent.duration._


object GameWorkerActor {

  def props(id: UUID)(implicit repository: Repository, materializer: ActorMaterializer) = Props(new GameWorkerActor(id))

  def name(id: UUID) = s"game-$id"

  // Command
  sealed trait Cmd

  case object Stop

  // Event
  sealed trait GameEvent extends Event

  case class GameParticipateCmd(
                                 country_code: String,
                                 game_code: String,
                                 customerId: String,
                                 transaction_code: Option[String],
                                 ean: Option[String],
                                 metadata: Map[String, String]
                               ) extends Cmd

  case class GamePlayCmd(
                          country_code: String,
                          customerId: String,
                          transaction_code: Option[String],
                          ean: Option[String],
                          metadata: Map[String, String]
                        ) extends Cmd


  case class GameParticipationEvent(
                                     timestamp: Instant = Instant.now,
                                     participationId: UUID,
                                     gameId: UUID,
                                     countryCode: String,
                                     customerId: String,
                                     instantwin: Option[InstantwinExtended] = None,
                                     transaction_code: Option[String],
                                     ean: Option[String],
                                     metadata: Map[String, String]
                                   ) extends GameEvent


}

class GameWorkerActor(gameId: UUID)(implicit val repository: Repository, val materializer: ActorMaterializer) extends PersistentActor with ActorLogging {

  context.setReceiveTimeout(120.minutes)

  val game: Option[Game] = Await.result(repository.game.getById(gameId, Seq(GameExtension.limits, GameExtension.eans)), Duration.Inf)

  var lastInstantWin: Option[InstantwinExtended] = None
  var nextInstantWins: List[InstantwinExtended] = List.empty[InstantwinExtended]
  var gameIsFinished: Boolean = false

  override def receiveRecover: Receive = {
    case event: GameParticipationEvent if event.instantwin.isDefined =>
      lastInstantWin = event.instantwin

    case RecoveryCompleted =>
      refreshInstantWins()
  }

  override def receiveCommand: Receive = {


    case cmd: GameParticipateCmd => try {

      getOrCreateCustomerWorkerActor(cmd.customerId) forward CustomerParticipateCmd(
        country_code = cmd.country_code,
        customerId = cmd.customerId,
        transaction_code = cmd.transaction_code,
        ean = cmd.ean,
        metadata = cmd.metadata,
        game = game.get
      )
    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case cmd: GamePlayCmd => try {

      val now = Instant.now()

      val event = GameParticipationEvent(
        timestamp = Instant.now(),
        participationId = UUID.randomUUID(),
        gameId = gameId,
        countryCode = cmd.country_code,
        customerId = cmd.customerId,
        instantwin = getInstantWin(now),
        transaction_code = cmd.transaction_code,
        ean = cmd.ean,
        metadata = cmd.metadata
      )

      // Return response

      // Win
      if (event.instantwin.isDefined) {
        // Persist event
        persistAsync(event) { _ =>
          sender() ! event
        }

        // Refresh instantWins list
        whichRefreshInstantWins()
      }
      // Lost
      else {
        sender() ! event
      }

    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

    case ReceiveTimeout ⇒ context.parent ! Passivate(stopMessage = Stop)
    case Stop ⇒ context.stop(self)
  }

  override def persistenceId: String = s"GAME-$gameId"


  def getInstantWin(instant: Instant): Option[InstantwinExtended] = {
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

  private def refreshInstantWins(): Unit = {
    nextInstantWins = Await.result(
      repository.instantwin.fetchWithPrizeBy(gameId)
        .filter(r => lastInstantWin.forall(l => (r.id.compareTo(l.id) > 0 && r.activate_date == l.activate_date) || r.activate_date.isAfter(l.activate_date)))
        .take(10).runWith(Sink.collection)
      , Duration.Inf)
    if (nextInstantWins.isEmpty)
      gameIsFinished = true
  }

  def getOrCreateCustomerWorkerActor(id: String): ActorRef = context.child(CustomerWorkerActor.name(id))
    .getOrElse(context.actorOf(CustomerWorkerActor.props(id), CustomerWorkerActor.name(id)))

}
