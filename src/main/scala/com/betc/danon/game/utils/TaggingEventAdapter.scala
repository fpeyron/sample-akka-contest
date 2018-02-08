package com.betc.danon.game.utils

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import com.betc.danon.game.actors.GameWorkerActor.GameParticipationEvent

class TaggingEventAdapter extends WriteEventAdapter {
  override def toJournal(event: Any): Any = event match {
    case event: GameParticipationEvent => Tagged(event, Set(event.customerId, event.countryCode))
    //case createdEvent: UserCreatedEvent => // add username to allow authentification query
//      Tagged(createdEvent, Set(createdEvent.userId.id.toString, Base64.encodeString(createdEvent.userEntry.username)))
  //  case userEvent: UserEvent => Tagged(userEvent, Set(userEvent.userId.id.toString))
    case _ => event
  }

  override def manifest(event: Any): String = ""
}
