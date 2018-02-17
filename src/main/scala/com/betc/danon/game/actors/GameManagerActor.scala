package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.GameManagerActor.{GameDeleteEvent, GameLinesEvent, GameUpdateEvent, ParticipateCmd}
import com.betc.danon.game.actors.GameWorkerActor.{GameParticipateCmd, GameStopCmd}
import com.betc.danon.game.models.GameEntity.{Game, GameStatus}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.JournalReader

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object GameManagerActor {

  final val name = "games"

  def props(gameActor: ActorRef)(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new GameManagerActor(gameActor))

  // Cmd
  case class ParticipateCmd(
                             country_code: String,
                             game_code: String,
                             customerId: String,
                             transaction_code: Option[String],
                             ean: Option[String],
                             meta: Map[String, String]
                           )

  // Event
  sealed trait Event

  case class GameCreateEvent(game: Game) extends Event

  case class GameUpdateEvent(game: Game) extends Event

  case class GameLinesEvent(id: UUID) extends Event

  case class GameDeleteEvent(id: UUID) extends Event

}

class GameManagerActor(gameActor: ActorRef)(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends Actor with ActorLogging {

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

      gameActor ! Passivate(stopMessage = GameStopCmd(gameId = game.id))
    }
    catch {
      case e: Exception => log.error("Exception caught: {}", e);
    }


    case GameDeleteEvent(id) => try {
      games = games.filterNot(_.id == id)
      gameActor ! Passivate(stopMessage = GameStopCmd(gameId = id))
    }
    catch {
      case e: Exception => log.error("Exception caught: {}", e);
    }


    case event: GameLinesEvent => try {
      gameActor ! Passivate(stopMessage = GameStopCmd(gameId = event.id))
    }
    catch {
      case e: Exception => log.error("Exception caught: {}", e);
    }


    case cmd: ParticipateCmd => try {

      // get Game in state
      val game: Option[Game] = games.find(r => r.countryCode == cmd.country_code && r.code == cmd.game_code && r.status == GameStatus.Activated)

      // Check if game exists
      if (game.isEmpty) {
        throw GameCodeNotFoundException(gameCode = cmd.game_code, countryCode = cmd.country_code)
      }

      // check if game is active start_date
      if (game.get.startDate.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(gameCode = cmd.game_code)
      }

      // check if game is active start_date
      if (game.get.endDate.isBefore(Instant.now)) {
        throw ParticipationCloseException(gameCode = cmd.game_code)
      }

      // forward to dedicated actor
      gameActor forward GameParticipateCmd(
        gameId = game.get.id,
        country_code = cmd.country_code,
        game_code = cmd.game_code,
        customerId = cmd.customerId,
        transaction_code = cmd.transaction_code,
        ean = cmd.ean,
        meta = cmd.meta
      )
    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }
  }
}

