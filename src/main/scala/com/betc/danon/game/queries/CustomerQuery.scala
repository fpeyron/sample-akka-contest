package com.betc.danon.game.queries

import java.util.UUID

import akka.stream.ActorMaterializer
import com.betc.danon.game.Repository
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipationEvent
import com.betc.danon.game.models.GameEntity.GameStatusType
import com.betc.danon.game.models.ParticipationDto.CustomerGameResponse
import com.betc.danon.game.utils.JournalReader

import scala.concurrent.{ExecutionContext, Future}

case class UnitStats(gameId: UUID, participations: Int, wins: Int)

trait CustomerQuery {

  implicit val repository: Repository
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext
  implicit val journalReader: JournalReader

  object customer {

    def getGames(countryCode: String, customerId: String, tag: Seq[String]): Future[Seq[CustomerGameResponse]] = {

      val result = for {

        games <- repository.game.findByTags(tag.head)

        participations <- {
          val gameIds = games.map(_.id)
          journalReader.currentEventsByPersistenceId(s"CUSTOMER-${customerId.toUpperCase}")
            .map(_.event)
            .collect {
              case event: CustomerParticipationEvent => (event.gameId, 1, event.instantwin.map(_ => 1).getOrElse(0))
            }
            .filter(event => gameIds.contains(event._1))
            .runFold(Map.empty[UUID, (Int, Int)]) { (current, event) =>
              current.filterNot(_._1 == event._1) + (event._1 -> current.get(event._1).map(c => (c._1 + event._2, c._2 + event._2)).getOrElse((event._2, event._3)))
            }
        }
      } yield (games, participations)

      result.map { result =>
        result._1
          .filter(r => r.country_code == countryCode && r.status == GameStatusType.Activated)
          .map(game => CustomerGameResponse(
            `type` = game.`type`,
            code = game.code,
            title = game.title,
            start_date = game.start_date,
            end_date = game.end_date,
            input_type = game.input_type,
            input_point = game.input_point,
            parents = Some(game.parents.flatMap(p => result._1.find(_.id == p)).map(_.code)).find(_.nonEmpty),
            participationCount = result._2.get(game.id).map(_._1).getOrElse(0),
            instantWinCount = result._2.get(game.id).map(_._2).getOrElse(0)
          ))
      }
    }
  }
}