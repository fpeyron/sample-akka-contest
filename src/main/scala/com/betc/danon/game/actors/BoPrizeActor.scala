package com.betc.danon.game.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.BoPrizeActor.{PrizeCreateCmd, PrizeDeleteCmd, PrizeUpdateCmd, _}
import com.betc.danon.game.models.PrizeDao.{PrizeCreateRequest, PrizeResponse}
import com.betc.danon.game.models.PrizeDomain.{Prize, PrizeType}
import com.betc.danon.game.utils.ActorUtil
import com.betc.danon.game.utils.AuthenticateSupport.UserContext
import com.betc.danon.game.utils.HttpSupport.{InvalidInputException, NotAuthorizedException, PrizeIdNotFoundException}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object BoPrizeActor {

  val name = "prize-singleton"

  def props(implicit repository: Repository) = Props(new BoPrizeActor)

  // Query
  sealed trait Query

  // Command
  sealed trait Cmd

  case class PrizeListQuery(uc: UserContext, game_id: Option[String]) extends Query

  case class PrizeGetQuery(uc: UserContext, id: UUID) extends Query

  case class PrizeCreateCmd(uc: UserContext, contestCreateRequest: PrizeCreateRequest) extends Cmd

  case class PrizeUpdateCmd(uc: UserContext, id: UUID, contestUpdateRequest: PrizeCreateRequest) extends Cmd

  case class PrizeDeleteCmd(uc: UserContext, id: UUID) extends Cmd

}

class BoPrizeActor(implicit val repository: Repository) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher


  override def receive: Receive = {


    case PrizeListQuery(uc, game_id) => try {

      sender ! repository.prize.fetchBy(country_code = Some(uc.country_code), game_id = game_id.flatMap(ActorUtil.string2UUID)).map(new PrizeResponse(_))

    } catch {
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case PrizeGetQuery(uc, id) => try {

      repository.prize.getById(id).map { prize =>

        // check existing prize
        if (!prize.exists(_.countryCode == uc.country_code)) {
          throw PrizeIdNotFoundException(id = id)
        } else {
          prize.map(new PrizeResponse(_)).get
        }
      }.pipeTo(sender)

    } catch {
      case e: PrizeIdNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case PrizeCreateCmd(uc, request) => try {

      // Validation input
      val request_error = checkPrizeInputForCreation(request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(fields = request_error.map(v => v._1 -> v._2).toMap)
      }

      // Persist
      val newId = UUID.randomUUID
      val prize = Prize(
        id = newId,
        code = request.code.getOrElse(newId.toString),
        countryCode = uc.country_code,
        `type` = request.`type`.map(PrizeType.withName) getOrElse PrizeType.Gift,
        title = request.title,
        label = request.label.getOrElse("Unknown"),
        description = request.description,
        picture = request.picture,
        vendorCode = request.vendor_code,
        faceValue = request.face_value,
        points = request.points
      )

      Await.result(repository.prize.create(prize), Duration.Inf)

      // Return prize

      sender ! new PrizeResponse(prize)
    } catch {
      case e: InvalidInputException => sender ! akka.actor.Status.Failure(e)
      case e: Exception => sender ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case PrizeUpdateCmd(uc, id, request) => try {

      // check existing contest
      val entity: Prize = Await.result(repository.prize.getById(id).map {
        case Some(u) if u.countryCode == uc.country_code => u
        case None => throw PrizeIdNotFoundException(id = id)
      }, Duration.Inf)

      /*
      // Check input payload
      val request_error = checkContestInputForUpdate(id, request)
      if (request_error.nonEmpty) {
        throw InvalidInputException(detail = request_error.map(v => v._1 -> v._2).toMap)
      }
      */

      // Persist upgrade
      val entityUpdated = Prize(
        id = entity.id,
        countryCode = entity.countryCode,
        code = request.code.getOrElse(entity.code),
        `type` = entity.`type`,
        title = request.title.map(Some(_)).getOrElse(entity.title),
        label = request.label.getOrElse(entity.label),
        description = request.description.map(Some(_)).getOrElse(entity.description),
        picture = request.picture.orElse(entity.picture),
        vendorCode = request.vendor_code.map(Some(_)).getOrElse(entity.vendorCode),
        faceValue = request.face_value.map(Some(_)).getOrElse(entity.faceValue),
        points = request.points.map(Some(_)).getOrElse(entity.points)
      )

      Await.result(repository.prize.update(entityUpdated), Duration.Inf)

      sender() ! new PrizeResponse(entityUpdated)

    } catch {
      case e: PrizeIdNotFoundException => sender() ! akka.actor.Status.Failure(e)
      case e: InvalidInputException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error("Exception caught: {}", e);
    }


    case PrizeDeleteCmd(uc, id) => try {

      // check existing contest
      Await.result(repository.prize.getById(id).map {
        case Some(u) if u.countryCode == uc.country_code => u
        case None => throw PrizeIdNotFoundException(id = id)
      }, Duration.Inf)

      Await.result(repository.prize.delete(id), Duration.Inf)

      sender ! None
    }
    catch {
      case e: PrizeIdNotFoundException => sender() ! scala.util.Failure(e)
      case e: NotAuthorizedException => sender() ! scala.util.Failure(e)
      case e: Exception => sender() ! scala.util.Failure(e); log.error("Exception caught: {}", e);
    }

  }


  private def checkPrizeInputForCreation(request: PrizeCreateRequest): Iterable[(String, String)] = {
    //Validation input
    Option(
      if (request.`type`.isEmpty)
        ("type", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.code.isDefined && request.code.exists(_.length < 2))
        ("code", s"INVALID_VALUE : should have more 2 chars") else null
    ) ++ Option(
      if (request.`type`.isDefined && !PrizeType.values.map(_.toString).contains(request.`type`.get))
        ("type", s"UNKNOWN_VALUE : list of values : ${PrizeType.all.mkString(",")}") else null
    ) ++ Option(
      if (request.label.isEmpty)
        ("label", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.label.exists(_.length < 2))
        ("label", s"INVALID_VALUE : should have more 2 chars") else null
    ) ++ Option(
      if (request.`type`.contains(PrizeType.Point.toString) && request.points.isEmpty)
        ("points", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.`type`.contains(PrizeType.GiftShop.toString) && request.vendor_code.isEmpty)
        ("vendor_code", s"MANDATORY_VALUE") else null
    ) ++ Option(
      if (request.`type`.contains(PrizeType.GiftShop.toString) && request.vendor_code.exists(_.length < 2))
        ("vendor_code", s"INVALID_VALUE : should have more 2 chars") else null
    ) ++ Option(
      if (request.`type`.contains(PrizeType.GiftShop.toString) && request.face_value.isEmpty)
        ("face_value", s"MANDATORY_VALUE") else null
    )
  }


}