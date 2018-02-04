package fr.sysf.sample.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import fr.sysf.sample.actors.ClusterSingletonActor.{GameDeleteEvent, GameLinesEvent, GameUpdateEvent}
import fr.sysf.sample.models.GameEntity.{Game, GameStatusType}
import fr.sysf.sample.utils.HttpSupport._
import fr.sysf.sample.{Config, Repository}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ClusterSingletonActor {

  def props(implicit repository: Repository, materializer: ActorMaterializer) = Props(new ClusterSingletonActor)

  final val name = "clusterSingleton"

  // Command
  sealed trait Cmd

  // Event
  sealed trait Event

  case class GameCreateEvent(game: Game) extends Event

  case class GameUpdateEvent(game: Game) extends Event

  case class GameLinesEvent(id: UUID) extends Event

  case class GameDeleteEvent(id: UUID) extends Event

}

class ClusterSingletonActor(implicit val repository: Repository, implicit val materializer: ActorMaterializer) extends Actor with ActorLogging {

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


    case cmd: ParticipationActor.ParticipateCmd => try {

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

      getOrCreateParticipationActor(game.get.id) forward cmd
    }
    catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

    case _ =>
      sender() ! s"${Config.Cluster.hostname}:${Config.Cluster.port}"

    //  ClusterSingletonActor.doItAsynchronously.pipeTo(self)

  }

  /*
  def forwardToActor: Actor.Receive = {
    case cmd: ParticipationActor.Cmd =>
      context.child(ParticipationActor.name(cmd.reference))
        .fold(context.actorOf(ParticipationActor.props(cmd.reference), ParticipationActor.name(cmd.reference)) forward cmd)(_ forward cmd)
  }
  */

  def getParticipationActor(id: UUID): Option[ActorRef] = context.child(ParticipationActor.name(id))

  def getOrCreateParticipationActor(id: UUID): ActorRef = context.child(ParticipationActor.name(id))
    .getOrElse(context.actorOf(ParticipationActor.props(id), ParticipationActor.name(id)))

}

