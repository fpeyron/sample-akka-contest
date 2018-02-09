package com.betc.danon.game.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.betc.danon.game.actors.CustomerWorkerActor.{CustomerCmd, CustomerGetParticipationQuery, CustomerParticipationState, CustomerQuery}
import com.betc.danon.game.actors.GameManagerActor.{GameDeleteEvent, GameFindQuery, GameLinesEvent, GameUpdateEvent}
import com.betc.danon.game.actors.GameWorkerActor.GameStopCmd
import com.betc.danon.game.models.GameEntity.{Game, GameLimit, GameStatus}
import com.betc.danon.game.models.ParticipationDto.{CustomerGameResponse, ParticipationStatus}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.JournalReader
import com.betc.danon.game.{Config, Repository}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object GameManagerActor {

  final val name = "games"

  def props(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new GameManagerActor)

  // Command
  sealed trait Cmd

  // Event
  sealed trait Event

  case class GameCreateEvent(game: Game) extends Event

  case class GameUpdateEvent(game: Game) extends Event

  case class GameLinesEvent(id: UUID) extends Event

  case class GameDeleteEvent(id: UUID) extends Event

  // Query
  sealed trait Query

  case class GameFindQuery(country_code: String, games: Seq[String], tags: Seq[String], customer_id: String) extends Query


  def msort(xs: List[Game]): List[Game] = {

    def less(a: Game, b: Game): Boolean = {
      if (b.parents.contains(a.id)) true
      else if (a.parents.contains(b.id)) false
      //else if (a.parent_id.isEmpty && b.parent_id.isEmpty) a.id.compareTo(b.id) > 0
      //else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get != b.parent_id.get) a.parent_id.get.compareTo(b.parent_id.get) > 0
      //else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get == b.parent_id.get) b.id.compareTo(a.id) > 0
      //else b.parent_id.getOrElse(b.id).compareTo(a.parent_id.getOrElse(a.id)) > 0
      else a.code.compareTo(b.code) < 0
    }

    def merge(xs: List[Game], ys: List[Game]): List[Game] = (xs, ys) match {
      case (Nil, _) => ys
      case (_, Nil) => xs
      case (x :: xs1, y :: ys1) =>
        if (less(x, y)) x :: merge(xs1, ys)
        else y :: merge(xs, ys1)
    }

    val n = xs.length / 2
    if (n == 0) xs
    else {
      val (ys, zs) = xs splitAt n
      merge(msort(ys), msort(zs))
    }
  }
}

class GameManagerActor(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends Actor with ActorLogging {

  var games: Seq[Game] = Seq.empty[Game]

  override def preStart(): Unit = {
    games = Await.result(
      repository.game.fetchExtendedBy()
        .filter(_.status == GameStatus.Activated).runWith(Sink.collection), Duration.Inf)
      .groupBy(_.id)
      .map(t => t._2.head.copy(limits = t._2.map(_.limits.toList).foldLeft(List.empty[GameLimit])((acc, item) => acc ::: item)))
      .toSeq
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
      case e: Exception => throw e
    }


    case GameDeleteEvent(id) => try {
      games = games.filterNot(_.id == id)
    }
    catch {
      case e: Exception => throw e
    }


    case event: GameLinesEvent => try {
      getGameWorkerActor(event.id).foreach(_ forward event)
    }
    catch {
      case e: Exception => throw e
    }


    case cmd: GameWorkerActor.GameParticipateCmd => try {

      // get Game in state
      val game: Option[Game] = games.find(r => r.countryCode == cmd.country_code && r.code == cmd.game_code && r.status == GameStatus.Activated)

      // check existing game
      if (!game.exists(_.countryCode == cmd.country_code)) {
        throw GameRefNotFoundException(code = cmd.game_code, country_code = cmd.country_code)
      }

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
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case query: GameFindQuery => try {

      // get Game in state
      val result: Seq[Game] = games
        .filter(
          r => r.countryCode == query.country_code
            && r.status == GameStatus.Activated
            && Some(query.tags).filterNot(_.isEmpty).forall(_.map(t => r.tags.contains(t)).forall(b => b))
            && Some(query.games).filterNot(_.isEmpty).forall(_.contains(r.code))
        )

      implicit val timeout: akka.util.Timeout = Config.Api.timeout
      val customerParticipations: Seq[CustomerParticipationState] = Await.result(
        getOrCreateCustomerWorkerActor(query.customer_id.toString) ? CustomerGetParticipationQuery(customerId = query.customer_id, gameIds = result.map(_.id)
        ), Duration.Inf) match {
        case response: Seq[Any] if response.isEmpty || response.headOption.exists(_.isInstanceOf[CustomerParticipationState]) =>
          response.asInstanceOf[Seq[CustomerParticipationState]]
        case _ => Seq.empty[CustomerParticipationState]
      }

      sender() ! GameManagerActor.msort(result.toList).map(game => CustomerGameResponse(
        `type` = game.`type`,
        code = game.code,
        title = game.title,
        start_date = game.startDate,
        end_date = game.endDate,
        input_type = game.inputType,
        input_point = game.inputPoint,
        parents = Some(game.parents.flatMap(p => games.find(_.id == p)).map(_.code)).find(_.nonEmpty),
        participation_count = customerParticipations.count(_.game_id == game.id),
        instant_win_count = customerParticipations.count(p => p.game_id == game.id && p.participationStatus == ParticipationStatus.Win)
      ))
    }
    catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case cmd: CustomerCmd => try {
      getOrCreateCustomerWorkerActor(cmd.customerId) forward cmd
    }
    catch {
      case e: Exception => throw e
    }

    case query: CustomerQuery => try {
      getOrCreateCustomerWorkerActor(query.customerId) forward query
    }
    catch {
      case e: Exception => throw e
    }

  }

  def getGameWorkerActor(id: UUID): Option[ActorRef] = context.child(GameWorkerActor.name(id))

  def getOrCreateGameWorkerActor(id: UUID): ActorRef = context.child(GameWorkerActor.name(id))
    .getOrElse(context.actorOf(GameWorkerActor.props(id), GameWorkerActor.name(id)))

  def getOrCreateCustomerWorkerActor(id: String): ActorRef = context.child(CustomerWorkerActor.name(id))
    .getOrElse(context.actorOf(CustomerWorkerActor.props(id), CustomerWorkerActor.name(id)))
}

