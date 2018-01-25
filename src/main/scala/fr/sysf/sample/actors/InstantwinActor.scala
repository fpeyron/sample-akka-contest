package fr.sysf.sample.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import fr.sysf.sample.actors.GameActor.GameGetInstantwinQuery
import fr.sysf.sample.actors.InstantwinActor.{InstanwinCreateCmd, InstanwinDeleteCmd, InstanwinUpdateCmd}
import fr.sysf.sample.models.GameEntity.GamePrize
import fr.sysf.sample.models.InstantwinDomain.Instantwin
import fr.sysf.sample.routes.HttpSupport.InvalidInputException


object InstantwinActor {

  def props(gameId: UUID) = Props(new InstantwinActor(gameId))

  def name(gameId: UUID) = s"instantwin-$gameId"

  // Command
  sealed trait Cmd

  case class InstanwinCreateCmd(request: GamePrize)

  case class InstanwinUpdateCmd(request: GamePrize)

  case class InstanwinDeleteCmd(GamePrize_id: Option[UUID] = None)

}

class InstantwinActor(game_id: UUID) extends Actor with ActorLogging {

  var state = Seq.empty[Instantwin]

  override def receive: Receive = {

    case GameGetInstantwinQuery(_, `game_id`) =>
      sender() ! state.filter(_.game_id == game_id).sortBy(_.attribution_date).toList


    case InstanwinCreateCmd(request) => try {
      state = state ++
        generateInstantWinDates(request.quantity, request.start_date, request.end_date)
          .map { date =>
            Instantwin(
              id = UUID.randomUUID(),
              game_id = game_id,
              gamePrize_id = request.id,
              prize_id = request.prize_id,
              activate_date = date
            )
          }
    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case InstanwinUpdateCmd(request) => try {
      state.filterNot(s => s.game_id == game_id && s.gamePrize_id == request.id) ++
        generateInstantWinDates(request.quantity, request.start_date, request.end_date)
          .map { date =>
            Instantwin(
              id = UUID.randomUUID(),
              game_id = game_id,
              gamePrize_id = request.id,
              prize_id = request.prize_id,
              activate_date = date
            )
          }
    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

    case InstanwinDeleteCmd(gamePrize_id) => try {
      state = state.filterNot(s => s.game_id == game_id && gamePrize_id.forall(_ == s.gamePrize_id))

      sender ! None
    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }

  }

  def generateInstantWinDates(quantity: Int, start_date: Instant, end_date: Instant): Seq[Instant] = {

    val r = scala.util.Random
    val pas = (end_date.toEpochMilli - start_date.toEpochMilli) / quantity

    for (i <- 0 until quantity) yield Instant.ofEpochMilli(start_date.toEpochMilli + (i * pas + r.nextFloat * pas).toLong)

  }

}
