package fr.sysf.sample.prize

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import fr.sysf.sample.DefaultDirectives.{EntityNotFoundException, InvalidInputException, NotAuthorizedException}
import fr.sysf.sample.prize.PrizeModel.PrizeCreateRequest


object PrizeActor {

  // Command
  sealed trait Cmd

  case class PrizeCreateCmd(contestCreateRequest: PrizeCreateRequest)

  case class PrizeUpdateCmd(id: UUID, contestUpdateRequest: PrizeCreateRequest) extends Cmd

  case class PrizeDeleteCmd(id: UUID) extends Cmd
}

class PrizeActor extends Actor with ActorLogging {

  import fr.sysf.sample.prize.PrizeModel._
  import fr.sysf.sample.prize.PrizeActor._

  var state = Seq.empty[PrizeResponse]


  def receive: Receive = {


    case PrizeListRequest => try {
      sender ! state.sortBy(c => c.label)

    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case PrizeGetRequest(id) => try {

      // check existing contest
      val contestResponse = state.find(c => c.id == id)
      if (contestResponse.isEmpty) {
        throw EntityNotFoundException(id = id)
      }

      sender ! contestResponse.get

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case PrizeCreateCmd(request) => try {

      /*
      // Validation input
      val request_error = checkContestInputForCreation(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }
      */

      // Persist
      val newId = UUID.randomUUID
      val contest = PrizeResponse(
        id = newId,
        `type` = request.`type`.map(PrizeType.withName) getOrElse PrizeType.Gift,
        title = request.title,
        label = request.label.getOrElse("Unknown"),
        description = request.description,
        vendor_code = request.vendor_code,
        face_value = request.face_value
      )
      state = state :+ contest

      // Return response
      sender ! contest

    } catch {
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case PrizeUpdateCmd(id, request) => try {

      // check existing contest
      val entity = state.find(c => c.id == id)
      if (entity.isEmpty) {
        throw EntityNotFoundException(id)
      }

      /*
      // Check input payload
      val request_error = checkContestInputForUpdate(id, request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }
      */

      // Persist updrade
      val entityUpdated = PrizeResponse(
        id = entity.get.id,
        `type` = entity.get.`type`,
        title = request.title.map(Some(_)).getOrElse(entity.get.title),
        label = request.label.getOrElse(entity.get.label),
        description = request.description.map(Some(_)).getOrElse(entity.get.description),
        vendor_code = request.vendor_code.map(Some(_)).getOrElse(entity.get.vendor_code),
        face_value = request.face_value.map(Some(_)).getOrElse(entity.get.face_value)
      )

      state = state.filterNot(_.id == entityUpdated.id) :+ entityUpdated
      sender ! entityUpdated

    } catch {
      case e: EntityNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); throw e
    }


    case PrizeDeleteCmd(id) => try {

      // check existing contest
      val contest = state.find(c => c.id == id)
      if (contest.isEmpty) {
        throw EntityNotFoundException(id)
      }


      state = state.filterNot(_.id == id)
      sender ! None
    }
    catch {
      case e: EntityNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); throw e
    }


  }
}