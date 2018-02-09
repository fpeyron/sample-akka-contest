package com.betc.danon.game.utils

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import com.betc.danon.game.actors.CustomerWorkerActor.CustomerParticipationEvent
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent

class TaggingEventAdapter extends WriteEventAdapter {
  override def toJournal(event: Any): Any = event match {
    case event: GameParticipationEvent => Tagged(event, Set(s"CUSTOMER-${event.customerId.toUpperCase}", s"COUNTRY-${event.countryCode.toUpperCase}"))
    case event: CustomerParticipationEvent => Tagged(event, Set(s"GAME-${event.gameId.toString}", s"COUNTRY-${event.countryCode.toUpperCase}"))
    case _ => event
  }

  override def manifest(event: Any): String = ""
}
