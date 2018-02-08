package com.betc.danon.game.utils

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipationEvent
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent

class TaggingEventAdapter extends WriteEventAdapter {
  override def toJournal(event: Any): Any = event match {
    case event: GameParticipationEvent => Tagged(event, Set(event.customerId, event.countryCode))
    case event: CustomerParticipationEvent => Tagged(event, Set(event.gameId.toString, event.countryCode))
    case _ => event
  }

  override def manifest(event: Any): String = ""
}
