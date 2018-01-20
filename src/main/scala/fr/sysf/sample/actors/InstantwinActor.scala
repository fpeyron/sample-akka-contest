package fr.sysf.sample.actors

import java.time.Instant
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import fr.sysf.sample.actors.InstantwinActor.{InstanwinCreateCmd, InstanwinDeleteCmd, InstanwinUpdateCmd}
import fr.sysf.sample.models.GameDomain.{GameGetInstantwinRequest, GameLineResponse}
import fr.sysf.sample.models.InstantwinDomain.Instantwin


object InstantwinActor {

  def props(gameId: UUID) = Props(new InstantwinActor(gameId))
  def name(gameId: UUID) = s"instantwin-$gameId"

  // Command
  sealed trait Cmd

  case class InstanwinCreateCmd(request: GameLineResponse)

  case class InstanwinUpdateCmd(request: GameLineResponse)

  case class InstanwinDeleteCmd(line_id: Option[UUID] = None)

}

class InstantwinActor(game_id: UUID) extends Actor with ActorLogging {

  var state = Seq.empty[Instantwin]

  override def receive: Receive = {

    case GameGetInstantwinRequest(_, game_id) =>
      sender() ! state.filter(_.game_id == game_id).sortBy(_.attributionDate).toList


    case InstanwinCreateCmd(request) =>
      state = state ++
        generateInstantWinDates(request.quantity, request.start_date, request.end_date)
          .map { date =>
            Instantwin(
              id = UUID.randomUUID(),
              game_id = game_id,
              gameLine_id = request.id,
              prize_id = request.prize_id,
              activateDate = date
            )
          }

    case InstanwinUpdateCmd(request) =>
      state.filterNot(s => s.game_id == game_id && s.gameLine_id == request.id) ++
        generateInstantWinDates(request.quantity, request.start_date, request.end_date)
          .map { date =>
            Instantwin(
              id = UUID.randomUUID(),
              game_id = game_id,
              gameLine_id = request.id,
              prize_id = request.prize_id,
              activateDate = date
            )
          }

    case InstanwinDeleteCmd(gameLine_id) =>
      state = state.filterNot(s => s.game_id == game_id && gameLine_id.forall(_ == s.gameLine_id))

  }

  def generateInstantWinDates(quantity: Int, start_date: Instant, end_date: Instant): Seq[Instant] = {

    val r = scala.util.Random
    val pas = (end_date.toEpochMilli - start_date.toEpochMilli) / quantity

    for (i <- 0 until quantity) yield Instant.ofEpochMilli(start_date.toEpochMilli + (i * pas + r.nextFloat * pas).toLong)

  }

}
