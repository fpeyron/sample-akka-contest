package com.betc.danon.game.actors

import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId}
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.betc.danon.game.actors.CustomerWorkerActor._
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GameStatus}
import com.betc.danon.game.models.InstantwinDomain.InstantwinExtended
import com.betc.danon.game.models.ParticipationDto.{CustomerGameAvailability, CustomerGameResponse, CustomerParticipateResponse, CustomerPrizeResponse, ParticipationStatus}
import com.betc.danon.game.models.PrizeDomain.PrizeType
import com.betc.danon.game.models.{Event, ParticipationDto}
import com.betc.danon.game.utils.HttpSupport._
import com.betc.danon.game.utils.{ActorUtil, JournalReader}
import com.betc.danon.game.{Config, Repository}

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._


object CustomerWorkerActor {

  def props(gameActor: ActorRef)(implicit repository: Repository, materializer: ActorMaterializer, journalReader: JournalReader) = Props(new CustomerWorkerActor(gameActor))

  val typeName: String = "customer"

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg: CustomerCmd => (s"${msg.customerId.toString}", msg)
    case msg: CustomerQry => (s"${msg.customerId.toString}", msg)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case msg: CustomerQry => s"$typeName-${math.abs(msg.customerId.hashCode()) % Config.Cluster.shardCount}"
    case msg: CustomerCmd => s"$typeName-${math.abs(msg.customerId.hashCode()) % Config.Cluster.shardCount}"
  }


  // Query
  sealed trait CustomerQry {
    def customerId: String
  }

  case class CustomerGetParticipationQry(countryCode: String, customerId: String, participationId: String) extends CustomerQry

  case class CustomerGetGamesQry(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerQry

  case class CustomerGetParticipationsQry(countryCode: String, customerId: String, tags: Seq[String], codes: Seq[String]) extends CustomerQry

  // Cmd
  sealed trait CustomerCmd {
    def customerId: String
  }

  case class CustomerStopCmd(customerId: String) extends CustomerCmd

  case class CustomerReloadCmd(customerId: String) extends CustomerCmd

  case class CustomerParticipateCmd(
                                     countryCode: String,
                                     customerId: String,
                                     transaction_code: Option[String],
                                     ean: Option[String],
                                     meta: Map[String, String],
                                     game: Game
                                   ) extends CustomerCmd

  case class CustomerResetGameParticipationsCmd(countryCode: String, customerId: String, gameCode: String) extends CustomerCmd

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

  case class CustomerParticipationReseted(
                                           timestamp: Instant = Instant.now,
                                           gameId: UUID,
                                           countryCode: String,
                                           customerId: String
                                         ) extends CustomerEvent

  case class CustomerParticipationState(participationId: UUID, gameId: UUID, countryCode: String, participationDate: Instant, participationStatus: ParticipationStatus.Value, prizeId: Option[UUID] = None)

}

class CustomerWorkerActor(gameActor: ActorRef)(implicit val repository: Repository, val materializer: ActorMaterializer, val journalReader: JournalReader) extends PersistentActor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher


  //override def persistenceId: String =  self.path.name
  override def persistenceId: String = s"$typeName-${self.path.name}"

  val customerId: String = self.path.name


  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    log.info(s">> RESTART ACTOR <${self.path.parent.name}-${self.path.name}> : ${reason.getMessage}")
  }

  override def preStart(): Unit = {
    super.preStart
    log.info(s">> START ACTOR <${self.path.parent.name}-${self.path.name}>")
  }

  override def postStop(): Unit = {
    super.postStop
    log.info(s">> STOP ACTOR <${self.path.parent.name}-${self.path.name}>")
  }


  override def receiveRecover: Receive = {

    case event: CustomerEvent => applyEvent(event)

    case RecoveryCompleted => context.setReceiveTimeout(1.minutes)

  }


  override def receiveCommand: Receive = {

    case CustomerGetParticipationQry(countryCode, _, participationId) => try {

      val participation = ActorUtil.string2UUID(participationId)
        .flatMap(p => participations.find(s => s.participationId == p && s.countryCode == countryCode))

      if (participation.isEmpty) {
        throw ParticipationNotFoundException(customerId = customerId, participationId = participationId)
      }

      if (participation.get.prizeId.isDefined && participation.get.participationStatus != ParticipationStatus.lost) {
        repository.prize.getById(participation.get.prizeId.get).map { prize =>
          CustomerParticipateResponse(
            id = participation.get.participationId,
            date = participation.get.participationDate,
            status = participation.get.participationStatus,
            prize = prize.map(new CustomerPrizeResponse(_))
          )
        }.pipeTo(sender())
      } else {
        sender() ! CustomerParticipateResponse(
          id = participation.get.participationId,
          date = participation.get.participationDate,
          status = participation.get.participationStatus
        )
      }


    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case CustomerGetParticipationsQry(countryCode, _, tags, codes) => try {

      val result = for {

        gameIds <- repository.game.findByTagsAndCodes(tags, codes)
          .filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
          .map(_.id)
          .runWith(Sink.seq)

        participations <- {
          journalReader.currentEventsByPersistenceId(persistenceId)
            .map(_.event)
            .collect {
              case event: CustomerParticipated => event
            }
            .filter(event => gameIds.contains(event.gameId))
            .map(event => CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = participations.find(_.participationId == event.participationId).map(_.participationStatus).getOrElse(ParticipationStatus.lost),
              prize = event.instantwin.map(i => new CustomerPrizeResponse(i.prize))
            ))
            .runWith(Sink.seq)
        }
      } yield participations

      result.pipeTo(sender)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case CustomerGetGamesQry(countryCode, _, tags, codes) => try {
      repository.game
        .findByTagsAndCodes(tags, codes).filter(g => g.countryCode == countryCode.toUpperCase && g.status == GameStatus.Activated)
        .runWith(Sink.seq)
        .map { games =>
          ParticipationDto.sortByParent(
            games.map { game =>
              CustomerGameResponse(
                `type` = game.`type`,
                code = game.code,
                title = game.title,
                picture = game.picture,
                description = game.description,
                start_date = game.startDate,
                end_date = game.endDate,
                input_type = game.inputType,
                input_point = game.inputPoint,
                parents = Some(game.limits.filter(_.`type` == GameLimitType.Dependency).flatMap(_.parent_id).flatMap(p => games.find(_.id == p)).map(_.code)).find(_.nonEmpty),
                participation_count = participations.count(_.gameId == game.id),
                instant_win_count = participations.count(p => p.gameId == game.id && p.participationStatus == ParticipationStatus.win),
                instant_toconfirm_count = participations.count(p => p.gameId == game.id && p.participationStatus == ParticipationStatus.toConfirm),
                availability = if (getDependenciesInFail(game).nonEmpty) CustomerGameAvailability.unavailableDependency
                else if (getParticipationLimitsInFail(game).nonEmpty) CustomerGameAvailability.unavailableLimit
                else CustomerGameAvailability.available
              )
            }.toList)
        }.pipeTo(sender)
    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case cmd: CustomerResetGameParticipationsCmd => try {

      val game = Await.result(
        repository.game.findByCode(cmd.gameCode)
          .filter(g => g.countryCode == cmd.countryCode.toUpperCase && g.status == GameStatus.Activated)
          .runWith(Sink.headOption)
        , Duration.Inf)

      if (game.isEmpty) {
        throw GameRefNotFoundException(country_code = cmd.countryCode, code = cmd.gameCode)
      }

      val events: immutable.Seq[CustomerParticipationReseted] = getResetParticipationEvents(game.get).to[collection.immutable.Seq]

      // fail is Game hasn't any children
      if (events.lengthCompare(2) < 0) {
        throw ParticipationResetException(code = cmd.gameCode)
      }

      persistAll(events) { _ => }

      events.filter(e => participations.exists(_.gameId == e.gameId)).foreach(applyEvent)

      sender() ! None

    }
    catch {
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
      val dependenciesInFail = getDependenciesInFail(game = cmd.game)
      if (dependenciesInFail.nonEmpty) {
        throw ParticipationDependenciesException(code = cmd.game.code, limits = dependenciesInFail)
      }

      // check Limits
      val participationLimitsInFail = getParticipationLimitsInFail(game = cmd.game)
      if (participationLimitsInFail.nonEmpty) {
        throw ParticipationLimitException(code = cmd.game.code, limits = participationLimitsInFail)
      }

      // check Ean
      if (cmd.game.inputType == GameInputType.Pincode && !cmd.ean.exists(e => cmd.game.inputEans.contains(e))) {
        throw ParticipationEanException(code = cmd.game.code, ean = cmd.ean)
      }

      // Return Lost if some GameLimit is reached
      (if (getWinLimitsInFail(cmd.game).nonEmpty) {
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
        Await.result(gameActor ? GameWorkerActor.GamePlayCmd(
          gameId = cmd.game.id,
          country_code = cmd.countryCode,
          customerId = cmd.customerId,
          transaction_code = cmd.transaction_code,
          ean = cmd.ean,
          meta = cmd.meta
        ), Duration.Inf)
      }) match {

        case e: GameParticipationEvent =>
          val event = new CustomerParticipated(e)

          persistAsync(event) { _ =>

            // Return response
            sender() ! CustomerParticipateResponse(
              id = event.participationId,
              date = event.timestamp,
              status = provideStatus(event.instantwin),
              prize = event.instantwin.map(p => new CustomerPrizeResponse(p.prize))
            )
          }

          // Refresh state list
          applyEvent(event)

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
      applyEvent(event)

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
      applyEvent(event)

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
      val event = CustomerParticipationInvalidated(
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
      applyEvent(event)

    } catch {
      case e: FunctionalException => sender() ! akka.actor.Status.Failure(e)
      case e: Exception => sender() ! akka.actor.Status.Failure(e); log.error(e.getMessage, e)
    }


    case _: ReceiveTimeout => context.parent ! Passivate(stopMessage = CustomerStopCmd(customerId))

    case _: CustomerStopCmd => context.stop(self)
  }


  var participations: Seq[CustomerParticipationState] = Seq.empty[CustomerParticipationState]

  def applyEvent: CustomerEvent => Unit = {
    case event: CustomerParticipated =>
      participations = participations :+ CustomerParticipationState(
        participationId = event.participationId,
        gameId = event.gameId,
        countryCode = event.countryCode,
        participationDate = event.timestamp,
        participationStatus = provideStatus(event.instantwin),
        prizeId = event.instantwin.map(_.prize.id)
      )
    case event: CustomerParticipationConfirmed =>
      participations = participations.filterNot(_.participationId == event.participationId) ++ participations.find(_.participationId == event.participationId).map(_.copy(participationStatus = ParticipationStatus.win))
    case event: CustomerParticipationValidated =>
      participations = participations.filterNot(_.participationId == event.participationId) ++ participations.find(_.participationId == event.participationId).map(_.copy(participationStatus = ParticipationStatus.win))
    case event: CustomerParticipationInvalidated =>
      participations = participations.filterNot(_.participationId == event.participationId) ++ participations.find(_.participationId == event.participationId).map(_.copy(participationStatus = ParticipationStatus.lost))
    case event: CustomerParticipationReseted =>
      participations = participations.filterNot(_.gameId == event.gameId)
  }

  private def getDependenciesInFail(game: Game): Seq[GameLimit] = game.limits.filter(_.`type` == GameLimitType.Dependency).filter { limit =>
    val now = Instant.now
    limit.unit match {
      case GameLimitUnit.Game if limit.parent_id.isDefined =>
        participations.count(p => p.gameId == limit.parent_id.get) < limit.value
      case GameLimitUnit.Second if limit.parent_id.isDefined =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.gameId == limit.parent_id.get && p.participationDate.isAfter(limitDate)) < limit.value
      case GameLimitUnit.Day if limit.parent_id.isDefined =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.gameId == limit.parent_id.get && p.participationDate.isAfter(limitDate)) < limit.value
    }
  }

  private def getParticipationLimitsInFail(game: Game): Seq[GameLimit] = game.limits.filter(_.`type` == GameLimitType.Participation).filter { limit =>
    val now = Instant.now
    limit.unit match {
      case GameLimitUnit.Game =>
        participations.count(p => p.gameId == game.id) >= limit.value
      case GameLimitUnit.Second =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.gameId == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
      case GameLimitUnit.Day =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.gameId == game.id && p.participationDate.isAfter(limitDate)) >= limit.value
    }
  }

  private def getWinLimitsInFail(game: Game): Seq[GameLimit] = game.limits.filter(_.`type` == GameLimitType.Win).filter { limit =>
    val now = Instant.now
    limit.unit match {
      case GameLimitUnit.Game =>
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus)) >= limit.value
      case GameLimitUnit.Second =>
        val limitDate = now.minusSeconds(limit.unit_value.getOrElse(1).toLong).minusNanos(1)
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus) && p.participationDate.isAfter(limitDate)) >= limit.value
      case GameLimitUnit.Day =>
        val limitDate = now.atZone(ZoneId.of(game.timezone)).truncatedTo(ChronoUnit.DAYS).minusDays(limit.unit_value.getOrElse(1).toLong).minusNanos(1).toInstant
        participations.count(p => p.gameId == game.id && Seq(ParticipationStatus.toConfirm, ParticipationStatus.pending, ParticipationStatus.win).contains(p.participationStatus) && p.participationDate.isAfter(limitDate)) >= limit.value
    }
  }

  private def provideStatus(instantwin: Option[InstantwinExtended]): ParticipationStatus.Value = instantwin.map(i => if (i.prize.`type` == PrizeType.Gift) ParticipationStatus.toConfirm else ParticipationStatus.pending).getOrElse(ParticipationStatus.lost)

  private def getResetParticipationEvents(game: Game): Seq[CustomerParticipationReseted] = {

    def internal(gameId: UUID): Seq[CustomerParticipationReseted] = {
      val games: Seq[Game] = Await.result(repository.game.findByParentId(gameId), Duration.Inf)
      if (games.isEmpty) {
        Seq.empty[CustomerParticipationReseted]
      } else {
        games.map(game => CustomerParticipationReseted(
          gameId = game.id,
          countryCode = game.countryCode,
          customerId = customerId
        )) ++ games.map(game => internal(game.id)).foldLeft(Seq.empty[CustomerParticipationReseted])(_ ++ _)
      }
    }

    internal(game.id) :+
      CustomerParticipationReseted(
        gameId = game.id,
        countryCode = game.countryCode,
        customerId = customerId
      )

  }
}
