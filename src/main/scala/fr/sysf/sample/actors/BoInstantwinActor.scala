package fr.sysf.sample.actors

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.remote.ContainerFormats.ActorRef
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, OverflowStrategy}
import fr.sysf.sample.Repository
import fr.sysf.sample.actors.BoInstantwinActor.{InstanwinCreateCmd, InstanwinDeleteCmd, InstanwinGetQuery, InstanwinUpdateCmd}
import fr.sysf.sample.models.GameEntity.GamePrize
import fr.sysf.sample.models.InstantwinDomain.Instantwin
import fr.sysf.sample.utils.HttpSupport.InvalidInputException

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object BoInstantwinActor {

  def props(gameId: UUID)(implicit repository: Repository, materializer: ActorMaterializer) = Props(new BoInstantwinActor(gameId))

  def name(gameId: UUID) = s"instantwin-$gameId"

  // Query
  sealed trait Query

  // Command
  sealed trait Cmd

  case class InstanwinGetQuery(mySender: ActorRef) extends Query

  case class InstanwinCreateCmd(request: GamePrize) extends Cmd

  case class InstanwinUpdateCmd(request: GamePrize) extends Cmd

  case class InstanwinDeleteCmd(gameprize_id: Option[UUID] = None) extends Cmd

}

class BoInstantwinActor(game_id: UUID)(implicit val repository: Repository, val materializer: ActorMaterializer) extends Actor with ActorLogging {

  override def receive: Receive = {

    case InstanwinGetQuery => try {

      sender ! repository.instantwin.fetchBy(game_id)
    } catch {
      case e: Exception => sender ! akka.actor.Status.Failure(e); throw e
    }


    case InstanwinCreateCmd(gameprize) => try {

      Await.result(repository.instantwin.insertAsStream(
        generateInstantWinDates(gameprize.quantity, gameprize.start_date, gameprize.end_date)
          .buffer(1000, OverflowStrategy.backpressure)
          .map { date =>
            Instantwin(
              id = UUID.randomUUID(),
              game_id = game_id,
              gameprize_id = gameprize.id,
              prize_id = gameprize.prize_id,
              activate_date = date
            )
          }
      ), Duration.Inf)

      sender ! gameprize

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case InstanwinUpdateCmd(request) => try {

      Await.result(repository.instantwin.deleteBy(game_id = game_id, Some(request.id)), Duration.Inf)
      Await.result(repository.instantwin.insertAsStream(
        generateInstantWinDates(request.quantity, request.start_date, request.end_date)
          //.buffer(1000, OverflowStrategy.backpressure)
          .map { date =>
          Instantwin(
            id = UUID.randomUUID(),
            game_id = game_id,
            gameprize_id = request.id,
            prize_id = request.prize_id,
            activate_date = date
          )
        }), Duration.Inf)

      sender ! None
    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case InstanwinDeleteCmd(gameprize_id) => try {
      Await.result(repository.instantwin.deleteBy(game_id = game_id, gameprize_id), Duration.Inf)

      sender ! None
    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

  }

  def generateInstantWinDates(quantity: Int, start_date: Instant, end_date: Instant): Source[Instant, NotUsed] = {

    val r = scala.util.Random
    val pas = (end_date.toEpochMilli - start_date.toEpochMilli) / quantity

    Source(1 to quantity)
      .map { i => Instant.ofEpochMilli(start_date.toEpochMilli + (i * pas + r.nextFloat * pas).toLong) }
  }

}
