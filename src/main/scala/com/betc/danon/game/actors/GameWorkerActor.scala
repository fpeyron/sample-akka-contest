package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipateCmd
import com.betc.danon.game.actors.GameWorkerActor._
import com.betc.danon.game.models.Event
import com.betc.danon.game.models.GameEntity.{Game, GameStatus}
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.utils.HttpSupport.{FunctionalException, GameCodeNotFoundException}
import com.betc.danon.game.utils.JournalReader
import com.betc.danon.game.{Config, Repository}

import scala.concurrent.Await
import scala.concurrent.duration._


object GameWorkerActor {

  def props(customerCluster: ActorRef)(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new GameWorkerActor(customerCluster))

  val typeName: String = "game"

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: GameCmd => (s"${msg.countryCode}-${msg.gameCode.toString}", msg)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: GameCmd => s"$typeName-${math.abs(msg.gameCode.hashCode()) % Config.Cluster.shardCount}"
  }

  // Command
  sealed trait GameCmd {
    def countryCode: String

    def gameCode: String
  }

  case class GameStopCmd(countryCode: String, gameCode: String) extends GameCmd

  case class GameParticipateCmd(
                                 countryCode: String,
                                 gameCode: String,
                                 customerId: String,
                                 transaction_code: Option[String],
                                 ean: Option[String],
                                 meta: Map[String, String]
                               ) extends GameCmd

  case class GamePlayCmd(
                          countryCode: String,
                          gameCode: String,
                          customerId: String,
                          transaction_code: Option[String],
                          ean: Option[String],
                          meta: Map[String, String]
                        ) extends GameCmd

  // Event
  sealed trait GameEvent extends Event

  case class GameParticipationEvent(
                                     timestamp: Instant = Instant.now,
                                     participationId: UUID,
                                     gameId: UUID,
                                     countryCode: String,
                                     customerId: String,
                                     instantwin: Option[InstantwinExtended] = None,
                                     transaction_code: Option[String] = None,
                                     ean: Option[String] = None,
                                     meta: Map[String, String] = Map.empty
                                   ) extends GameEvent


}

class GameWorkerActor(customerCluster: ActorRef)(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends PersistentActor with ActorLogging {

  override def persistenceId: String = s"game-${self.path.name}"


  override def postRestart(reason: Throwable): Unit = {
    log.debug(s">> RESTART ACTOR <${self.path.parent.name}-${self.path.name}> : ${reason.getMessage}")
    super.postRestart(reason)
  }

  override def preStart(): Unit = {
    log.debug(s">> START ACTOR <${self.path.parent.name}-${self.path.name}>")
    val gameCountryCode: String = self.path.name.split("-")(0)
    val gameCode: String = self.path.name.split("-")(1)
    game = Await.result(repository.game.fetchByCode(gameCode).filter(r => r.countryCode == gameCountryCode && r.status == GameStatus.Activated).runWith(Sink.headOption), Duration.Inf)
    context.setReceiveTimeout(1.minutes)
    super.preStart
  }

  override def postStop(): Unit = {
    log.debug(s">> STOP ACTOR <${self.path.parent.name}-${self.path.name}>")
    super.postStop
  }


  override def receiveRecover: Receive = {
    case event: GameParticipationEvent if event.instantwin.isDefined =>
      lastInstantWin = event.instantwin

    case RecoveryCompleted =>
      refreshInstantWins()
  }

  override def receiveCommand: Receive = {


    case cmd: GameParticipateCmd => try {

      if (game.isEmpty) {
        throw GameCodeNotFoundException(countryCode = cmd.countryCode, gameCode = cmd.gameCode)
      }

      customerCluster forward CustomerParticipateCmd(
        countryCode = game.get.countryCode,
        customerId = cmd.customerId,
        transaction_code = cmd.transaction_code,
        ean = cmd.ean,
        meta = cmd.meta,
        game = game.get
      )

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case cmd: GamePlayCmd => try {

      if (game.isEmpty) {
        throw GameCodeNotFoundException(countryCode = cmd.countryCode, gameCode = cmd.gameCode)
      }

      val now = Instant.now()

      val event = GameParticipationEvent(
        timestamp = Instant.now(),
        participationId = UUID.randomUUID(),
        gameId = game.get.id,
        countryCode = cmd.countryCode,
        customerId = cmd.customerId,
        instantwin = getInstantWin(now),
        transaction_code = cmd.transaction_code,
        ean = cmd.ean,
        meta = cmd.meta
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
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case _: ReceiveTimeout =>
      context.parent ! Passivate(stopMessage = GameStopCmd(countryCode = game.get.countryCode, gameCode = game.get.code))

    case _: GameStopCmd =>
      context.stop(self)
  }


  var game: Option[Game] = None
  var lastInstantWin: Option[InstantwinExtended] = None
  var nextInstantWins: List[InstantwinExtended] = List.empty[InstantwinExtended]
  var gameIsFinished: Boolean = false


  private def getInstantWin(instant: Instant): Option[InstantwinExtended] = {
    val instantWin = nextInstantWins.find(_.activateDate.isBefore(instant))

    log.debug(s"lastInstantWin: \t${lastInstantWin.map(_.id)}")
    log.debug(s"instantWin: \t${instantWin.map(_.id)}")
    log.debug(s"nextInstantWins: \t${nextInstantWins.map(t => t.id + " : " + t.activateDate).take(2).mkString("\t")}")

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
      repository.instantwin.fetchWithPrizeBy(game.get.id)
        .filter(r => lastInstantWin.forall(l => (r.id.compareTo(l.id) > 0 && r.activateDate == l.activateDate) || r.activateDate.isAfter(l.activateDate)))
        .take(10).runWith(Sink.collection)
      , Duration.Inf)
    if (nextInstantWins.isEmpty)
      gameIsFinished = true
  }

}
