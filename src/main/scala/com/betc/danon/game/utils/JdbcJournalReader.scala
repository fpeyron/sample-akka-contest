package com.betc.danon.game.utils

import java.time.Instant

import akka.NotUsed
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.betc.danon.game.models.Event

case class JdbcJournalReader()
                            (implicit materializer: ActorMaterializer) extends JournalReader {

  override def currentEventsByTag(tag: String): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByTag(tag, 0)

  override def currentEventsByPersistenceId(persistenceId: String): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByPersistenceId(persistenceId, 0, Long.MaxValue)

  override def eventsByTag(tag: String): Source[EventEnvelope, NotUsed] =
    journal.eventsByTag(tag, 0)

  private def tryGetEventTime(event: Any): Option[Instant] = event match {
    case e: Event => Some(e.timestamp)
    case _ => None
  }

  override def newEventsByTag(tag: String): Source[EventEnvelope, NotUsed] = {
    val now = Instant.now()
    val isNotNew = (envelope: EventEnvelope) => !tryGetEventTime(envelope.event).exists(now.compareTo(_) < 0)
    eventsByTag(tag).dropWhile(isNotNew)
  }

  private def journal(implicit materializer: ActorMaterializer) =
    PersistenceQuery(materializer.system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
}
