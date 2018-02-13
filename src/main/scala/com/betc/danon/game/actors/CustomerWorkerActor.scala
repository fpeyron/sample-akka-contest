package com.betc.danon.game.actors

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor._
import com.betc.danon.game.actors.GameWorkerActor.{GameParticipationEvent, GameStopCmd}
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GameStatus}
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.models.ParticipationDto.{CustomerGameResponse, CustomerParticipateResponse, ParticipationStatus}
import com.betc.danon.game.models.PrizeDao.PrizeResponse
import com.betc.danon.game.models.PrizeDomain.PrizeType
import com.betc.danon.game.models.{Event, GameEntity}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.{ActorUtil, JournalReader}

import scala.concurrent.Await
import scala.concurrent.duration._


object CustomerWorkerActor {

  def props(id: String)(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new CustomerWorkerActor(id))

  def name(id: String) = s"customer-$id"


  // Query
  sealed trait CustomerQuery { def customerId: String}

  case class CustomerGetParticipationQuery(customerId: String, gameIds: Seq[UUID]) extends CustomerQuery

  case class CustomerGetGamesQuery(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerQuery


  // Cmd
  sealed trait CustomerCmd { def customerId: String}

  case object CustomerStopCmd

  case class CustomerReloadCmd(customerId: String) extends CustomerCmd

  case class CustomerGetParticipationsCmd(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerCmd

  case class CustomerParticipateCmd(
                                     countryCode: String,
                                     customerId: String,
                                     transaction_code: Option[String],
                                     ean: Option[String],
                                     meta: Map[String, String],
                                     game: Game
                                   ) extends CustomerCmd

  case class CustomerConfirmParticipationCmd(
                                              countryCode: String,
                                              customerId: String,
                                              participationId: String,
                                              meta: Map[String, String]
                                            ) extends CustomerCmd

  case class CustomerValidateParticipationCmd(
                                               countryCode: String,
                                               customerId: String,
                                               participationId: String,
                                               meta: Map[String, String]
                                             ) extends CustomerCmd

  case class CustomerInvalidateParticipationCmd(
                                                 countryCode: String,
                                                 customerId: String,
                                                 participationId: String,
                                                 meta: Map[String, String]
                                               ) extends CustomerCmd


  // Event
  sealed trait CustomerEvent extends Event

  case class CustomerParticipated(
                                   timestamp: Instant = Instant.now,
                                   participationId: UUID,
                                   gameId: UUID,
                                   countryCode: String,
                                   customerId: String,
                                   instantwin: Option[InstantwinExtended] = None,
                                   transaction_code: Option[String] = None,
                                   ean: Option[String] = None,
                                   meta: Map[String, String] = Map.empty
                                 ) extends CustomerEvent {
    def this(r: GameParticipationEvent) = this(timestamp = r.timestamp, participationId = r.participationId, gameId = r.gameId, countryCode = r.countryCode, customerId = r.customerId, instantwin = r.instantwin, transaction_code = r.transaction_code, ean = r.ean, meta = r.meta)
  }

  case class CustomerParticipationConfirmed(
                                             timestamp: Instant = Instant.now,
                                             participationId: UUID,
                                             gameId: UUID,
                                             countryCode: String,
                                             customerId: String,
                                             meta: Map[String, String] = Map.empty
                                           ) extends CustomerEvent

  case class CustomerParticipationValidated(
                                             timestamp: Instant = Instant.now,
                                             participationId: UUID,
                                             gameId: UUID,
                                             countryCode: String,
                                             customerId: String,
                                             meta: Map[String, String] = Map.empty
                                           ) extends CustomerEvent

  case class CustomerParticipationInvalidated(
                                               timestamp: Instant = Instant.now,
                                               participationId: UUID,
                                               gameId: UUID,
                                               countryCode: String,
                                               customerId: String,
                                               meta: Map[String, String] = Map.empty
                                             ) extends CustomerEvent

  case class CustomerParticipationState(participationId: UUID, gameId: UUID, countryCode: String, participationDate: Instant, participationStatus: ParticipationStatus.Value)

}

class CustomerWorkerActor(customerId: String)(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends PersistentActor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  context.setReceiveTimeout(15.minutes)

  var participations: Seq[CustomerParticipationState] = Seq.empty[CustomerParticipationState]


  override def receiveRecover: Receive = {

    case event: CustomerParticipated =>
      participations = participations :+
        CustomerParticipationState(
          participationId = event.participationId,
          gameId = event.gameId,
          countryCode = event.countryCode,
          participationDate = event.timestamp,
          participationStatus = provideStatus(event.instantwin)
        )

    case event: CustomerParticipationConfirmed =>
      participations = participations.filterNot(_.participationId == event.participationId) :+
        participations.find(_.participationId == event.participationId)
          .map(_.copy(participationStatus = ParticipationStatus.win)).orNull

    case event: CustomerParticipationValidated =>
      participations = participations.filterNot(_.participationId == event.participationId) :+
        participations.find(_.participationId == event.participationId)
          .map(_.copy(participationStatus = ParticipationStatus.win)).orNull

    case event: CustomerParticipationInvalidated =>
      participations = participations.filterNot(_.participationId == event.participationId) :+
        participations.find(_.participationId == event.participationId)
          .map(_.copy(participationStatus = ParticipationStatus.lost)).orNull


    case RecoveryCompleted =>

  }


  override def receiveCommand: Receive = {

    case CustomerGetParticipationQuery(_, gameIds) => try {
      sender() ! participations.filter(p => gameIds.contains(p.gameId))
    }


    case CustomerGetParticipationsCmd(countryCode, _, tags, codes) => try {

      val result = for {

        gameIds <- repository.game.findByTagsAndCodes(tags, codes)
          .filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
          .map(_.id)
          .runWith(Sink.seq)

        participations <- {
          journalReader.currentEventsByPersistenceId(s"CUSTOMER-${customerId.toUpperCase}")
            .map(_.event)
            .collect {
              case event: CustomerParticipated => event
            }
            .filter(event => gameIds.contains(event.gameId))
            .map(event => CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = participations.find(_.participationId == event.participationId).map(_.participationStatus).getOrElse(ParticipationStatus.lost),
              prize = event.instantwin.map(i => new PrizeResponse(i.prize))
            ))
            .runWith(Sink.seq)
        }
      } yield participations

      result.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case CustomerGetGamesQuery(countryCode, _, tags, codes) => try {
      repository.game
        .findByTagsAndCodes(tags, codes).filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
        .runWith(Sink.seq)
        .map { games =>
          GameEntity.sortByParent(games.toList)
            .map(game =>
              CustomerGameResponse(
                `type` = game.`type`,
                code = game.code,
                title = game.title,
                start_date = game.startDate,
                end_date = game.endDate,
                input_type = game.inputType,
                input_point = game.inputPoint,
                parents = Some(game.parents.flatMap(p => games.find(_.id == p)).map(_.code)).find(_.nonEmpty),
                participation_count = participations.count(_.gameId == game.id),
                instant_win_count = participations.count(p => p.gameId == game.id && p.participationStatus == ParticipationStatus.win),
                instant_toconfirm_count = participations.count(p => p.gameId == game.id && p.participationStatus == ParticipationStatus.toConfirm)
              ))
        }.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerParticipateCmd => try {

      log.debug(s"$customerId : participate to ${cmd.game.id}")

      // check Status
      if (cmd.game.status != GameStatus.Activated) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active start_date
      if (cmd.game.startDate.isAfter(Instant.now)) {
        throw ParticipationNotOpenedException(code = cmd.game.code)
      }

      // check if game is active end_date
      if (cmd.game.endDate.isBefore(Instant.now)) {
        throw ParticipationCloseException(code = cmd.game.code)
      }

      // check Dependencies
      if (hasParticipationDependencies(game = cmd.game)) {
        throw ParticipationDependenciesException(code = cmd.game.code)
      }

      // check Limits
      val participationLimitsInFail = getParticipationLimitsInFail(game = cmd.game)
      if (participationLimitsInFail.exists(_.`type` == GameLimitType.Participation)) {
        throw ParticipationLimitException(code = cmd.game.code)
      }

      // check Ean
      if (cmd.game.inputType == GameInputType.Pincode && !cmd.ean.exists(e => cmd.game.inputEans.contains(e))) {
        throw ParticipationEanException(code = cmd.game.code)
      }

      // Return Lost if some GameLimit is reached
      (if (participationLimitsInFail.exists(_.`type` == GameLimitType.Win)) {
        GameParticipationEvent(
          timestamp = Instant.now,
          participationId = UUID.randomUUID(),
          gameId = cmd.game.id,
          countryCode = cmd.countryCode,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          meta = cmd.meta
        )
      }
      else {
        implicit val timeout: Timeout = Timeout(1.minutes)
        Await.result(getOrCreateGameWorkerActor(cmd.game.id) ? GameWorkerActor.GamePlayCmd(
          country_code = cmd.countryCode,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          meta = cmd.meta
        ), Duration.Inf)
      }) match {

        case event: GameParticipationEvent =>
          persistAsync(new CustomerParticipated(event)) { _ =>

            // Return response
            sender() ! CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = provideStatus(event.instantwin),
              prize = event.instantwin.map(p => new PrizeResponse(p.prize))
            )
          }

          // Refresh state list
          participations = participations :+ CustomerParticipationState(
            participationId = event.participationId,
            gameId = event.gameId,
            countryCode = event.countryCode,
            participationDate = event.timestamp,
            participationStatus = provideStatus(event.instantwin)
          )

        case _ => sender() ! _
      }
    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerConfirmParticipationCmd => try {

      // check participationId
      val participationId = ActorUtil.string2UUID(cmd.participationId)
      if (participationId.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check participation exists
      val participation = participations.find(p => p.participationId == participationId.get)
      if (participation.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check status of participation game is active start_date
      if (participation.get.participationStatus != ParticipationStatus.toConfirm) {
        throw ParticipationConfirmException(customerId = customerId, participationId = cmd.participationId, participation.get.participationStatus)
      }

      // Return
      val event = CustomerParticipationConfirmed(
        participationId = participation.get.participationId,
        gameId = participation.get.gameId,
        countryCode = participation.get.countryCode,
        customerId = cmd.customerId,
        meta = cmd.meta
      )
      //val originalSender = sender
      persistAsync(event) { _ =>
        // Return response
        sender() ! None
      }
      // Refresh state list
      participations = participations.filterNot(_.participationId == participationId.get) :+ participation.get.copy(participationStatus = ParticipationStatus.win)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerValidateParticipationCmd => try {

      // check participationId
      val participationId = ActorUtil.string2UUID(cmd.participationId)
      if (participationId.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check participation exists
      val participation = participations.find(p => p.participationId == participationId.get)
      if (participation.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check status of participation game is active start_date
      if (participation.get.participationStatus != ParticipationStatus.pending) {
        throw ParticipationConfirmException(customerId = customerId, participationId = cmd.participationId, participation.get.participationStatus)
      }

      // Return
      val event = CustomerParticipationValidated(
        participationId = participation.get.participationId,
        gameId = participation.get.gameId,
        countryCode = participation.get.countryCode,
        customerId = cmd.customerId,
        meta = cmd.meta
      )
      //val originalSender = sender
      persistAsync(event) { _ =>
        // Return response
        sender() ! None
      }
      // Refresh state list
      participations = participations.filterNot(_.participationId == participationId.get) :+ participation.get.copy(participationStatus = ParticipationStatus.win)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerInvalidateParticipationCmd => try {

      // check participationId
      val participationId = ActorUtil.string2UUID(cmd.participationId)
      if (participationId.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check participation exists
      val participation = participations.find(p => p.participationId == participationId.get && p.countryCode == cmd.countryCode)
      if (participation.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = cmd.participationId)
      }

      // check status of participation game is active start_date
      if (participation.get.participationStatus != ParticipationStatus.pending) {
        throw ParticipationConfirmException(customerId = customerId, participationId = cmd.participationId, participation.get.participationStatus)
      }

      // Return
      val event = CustomerParticipationValidated(
        participationId = participation.get.participationId,
        gameId = participation.get.gameId,
        countryCode = participation.get.countryCode,
        customerId = customerId,
        meta = cmd.meta
      )
      //val originalSender = sender
      persistAsync(event) { _ =>
        // Return response
        sender() ! None
      }
      // Refresh state list
      participations = participations.filterNot(_.participationId == participationId.get) :+ participation.get.copy(participationStatus = ParticipationStatus.lost)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case ReceiveTimeout ⇒ context.parent ! Passivate(stopMessage = CustomerStopCmd)

    case GameStopCmd ⇒ context.stop(self)
  }

  override def persistenceId: String = s"CUSTOMER-${customerId.toUpperCase}"


  private def getOrCreateGameWorkerActor(id: UUID): ActorRef = context.child(GameWorkerActor.name(id))
    .getOrElse(context.actorOf(GameWorkerActor.props(id), GameWorkerActor.name(id)))

  private def hasParticipationDependencies(game: Game): Boolean = !Some(game.parents).filter(_.nonEmpty).forall(_.exists(parent => participations.exists(_.gameId == parent)))

  private def getParticipationLimitsInFail(game: Game): Seq[GameLimit] = game.limits.filter { limit =>
    val now = Instant.now
    limit.unit match {
      case GameLimitUnit.Game if limit.`type` == GameLimitType.Participation =>
        participations.count(p => p.gameId == game.id) >= limit.value
      case GameLimitUnit.Game if limit.`type` == GameLimitType.Win =>
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus)) >= limit.value

      case GameLimitUnit.Second if limit.`type` == GameLimitType.Participation =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.gameId == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
      case GameLimitUnit.Second if limit.`type` == GameLimitType.Win =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus) && p.participationDate.isAfter(limitDate)) >= limit.value

      case GameLimitUnit.Day if limit.`type` == GameLimitType.Participation =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.gameId == game.id && p.participationDate.isAfter(limitDate)) >= limit.value

      case GameLimitUnit.Day if limit.`type` == GameLimitType.Win =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus) && p.participationDate.isAfter(limitDate)) >= limit.value
    }
  }

  private def provideStatus(instantwin: Option[InstantwinExtended]): ParticipationStatus.Value = instantwin.map(i => if (i.prize.`type` == PrizeType.Gift) ParticipationStatus.toConfirm else ParticipationStatus.pending).getOrElse(ParticipationStatus.lost)
}
