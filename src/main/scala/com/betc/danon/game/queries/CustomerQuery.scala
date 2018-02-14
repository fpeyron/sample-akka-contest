package com.betc.danon.game.queries

import java.util.UUID

import akka.stream.ActorMaterializer
import com.betc.danon.game.Repository
import com.betc.danon.game.utils.JournalReader

import scala.concurrent.ExecutionContext

case class UnitStats(gameId: UUID, participations: Int, wins: Int)

trait CustomerQuery {

  implicit val repository: Repository
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext
  implicit val journalReader: JournalReader

  object customer {

  }

}