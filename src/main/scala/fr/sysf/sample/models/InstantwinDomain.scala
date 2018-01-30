package fr.sysf.sample.models

import java.time.Instant
import java.util.UUID

object InstantwinDomain {

  case class Instantwin(
                         id: UUID,
                         game_id: UUID,
                         gameprize_id: UUID,
                         prize_id: UUID,
                         activate_date: Instant
                       )

}
