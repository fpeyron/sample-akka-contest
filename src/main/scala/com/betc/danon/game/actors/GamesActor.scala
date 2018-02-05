package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.actors.GamesActor.{GameDeleteEvent, GameLinesEvent, GameUpdateEvent}
import com.betc.danon.game.models.GameEntity.{Game, GameStatusType}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.{Config, Repository}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object GamesActor {

  final val name = "games"

  def props(implicit repository: Repository, materializer: ActorMaterializer) = Props(new GamesActor)

  // Command
  sealed trait Cmd

  // Event
  sealed trait Event

  case class GameCreateEvent(game: Game) extends Event

  case class GameUpdateEvent(game: Game) extends Event

  case class GameLinesEvent(id: UUID) extends Event

  case class GameDeleteEvent(id: UUID) extends Event

}

class GamesActor(implicit val repository: Repository, implicit val materializer: ActorMaterializer) extends Actor with ActorLogging {

  var games: Seq[Game] = Seq.empty[Game]

  override def preStart(): Unit = {
    games = Await.result(
      repository.game.fetchBy()
        .filter(_.status == GameStatusType.Activated).runWith(Sink.collection), Duration.Inf)
  }

  override def receive: Receive = {

    case GameUpdateEvent(game) =>
      if (game.status == GameStatusType.Activated)
        games = games.filter(_.id == game.id) :+ game.copy(prizes = Seq.empty, input_eans = Seq.empty, input_freecodes = Seq.empty)
      else
        games = games.filter(_.id == game.id)


    case GameDeleteEvent(id) =>
      games = games.filter(_.id == id)


    case event: GameLinesEvent =>
      getParticipationActor(event.id).foreach(_ forward event)


    case cmd: GameParticipationActor.ParticipateCmd => try {

      // get Game in state
      val game: Option[Game] = games.find(r => r.country_code == cmd.country_code && r.code == cmd.game_code && r.status == GameStatusType.Activated)

      // check existing game
      if (!game.exists(_.country_code == cmd.country_code)) {
        throw GameRefNotFoundException(code = cmd.game_code, country_code = cmd.country_code)
      }

      // check if game is active start_date
      if (game.get.start_date.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(code = cmd.game_code)
      }

      // check if game is active start_date
      if (game.get.end_date.isBefore(Instant.now)) {
        throw ParticipationCloseException(code = cmd.game_code)
      }

      // forward to dedicated actor
      getOrCreateParticipationActor(game.get.id) forward cmd
    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

    case _ =>
      sender() ! s"${Config.Cluster.hostname}:${Config.Cluster.port}"


  }

  def getParticipationActor(id: UUID): Option[ActorRef] = context.child(GameParticipationActor.name(id))

  def getOrCreateParticipationActor(id: UUID): ActorRef = context.child(GameParticipationActor.name(id))
    .getOrElse(context.actorOf(GameParticipationActor.props(id), GameParticipationActor.name(id)))

}

