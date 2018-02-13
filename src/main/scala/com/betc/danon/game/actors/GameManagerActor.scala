package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor.{CustomerCmd, CustomerQuery}
import com.betc.danon.game.actors.GameManagerActor.{GameDeleteEvent, GameLinesEvent, GameUpdateEvent}
import com.betc.danon.game.actors.GameWorkerActor.GameStopCmd
import com.betc.danon.game.models.GameEntity.{Game, GameStatus}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.JournalReader

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object GameManagerActor {

  final val name = "games"

  def props(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new GameManagerActor)

  // Event
  sealed trait Event

  case class GameCreateEvent(game: Game) extends Event

  case class GameUpdateEvent(game: Game) extends Event

  case class GameLinesEvent(id: UUID) extends Event

  case class GameDeleteEvent(id: UUID) extends Event

}

class GameManagerActor(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends Actor with ActorLogging {

  var games: Seq[Game] = Seq.empty[Game]

  override def preStart(): Unit = {
    games = Await.result(
      repository.game.fetchExtendedBy().filter(_.status == GameStatus.Activated).runWith(Sink.seq), Duration.Inf)
  }

  override def receive: Receive = {

    case GameUpdateEvent(game) => try {
      if (game.status == GameStatus.Activated)
        games = games.filterNot(_.id == game.id) :+ game.copy(prizes = Seq.empty, inputEans = Seq.empty, inputFreecodes = Seq.empty)
      else
        games = games.filterNot(_.id == game.id)

      getGameWorkerActor(game.id).foreach(_ ! GameStopCmd)
    }
    catch {
      case e: Exception => log.error(e.getMessage, e)
    }


    case GameDeleteEvent(id) => try {
      games = games.filterNot(_.id == id)
    }
    catch {
      case e: Exception => log.error(e.getMessage, e)
    }


    case event: GameLinesEvent => try {
      getGameWorkerActor(event.id).foreach(_ forward event)
    }
    catch {
      case e: Exception => log.error(e.getMessage, e)
    }


    case cmd: GameWorkerActor.GameParticipateCmd => try {

      // get Game in state
      val game: Option[Game] = games.find(r => r.countryCode == cmd.country_code && r.code == cmd.game_code && r.status == GameStatus.Activated)

      // check if game is active start_date
      if (game.get.startDate.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(code = cmd.game_code)
      }

      // check if game is active start_date
      if (game.get.endDate.isBefore(Instant.now)) {
        throw ParticipationCloseException(code = cmd.game_code)
      }

      // forward to dedicated actor
      getOrCreateGameWorkerActor(game.get.id) forward cmd
    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerCmd => getOrCreateCustomerWorkerActor(cmd.customerId) forward cmd

    case query: CustomerQuery => getOrCreateCustomerWorkerActor(query.customerId) forward query
  }

  def getGameWorkerActor(id: UUID): Option[ActorRef] = context.child(GameWorkerActor.name(id))

  def getOrCreateGameWorkerActor(id: UUID): ActorRef = context.child(GameWorkerActor.name(id))
    .getOrElse(context.actorOf(GameWorkerActor.props(id), GameWorkerActor.name(id)))

  def getOrCreateCustomerWorkerActor(id: String): ActorRef = context.child(CustomerWorkerActor.name(id))
    .getOrElse(context.actorOf(CustomerWorkerActor.props(id), CustomerWorkerActor.name(id)))
}

