package fr.sysf.sample.models

import java.time.Instant
import java.util.UUID

object InstantwinDomain {

  case class Instantwin(
                         id: UUID,
                         game_id: UUID,
                         gameLine_id: UUID,
                         prize_id: UUID,
                         activateDate: Instant,
                         attributionDate: Option[Instant] = None,
                         state: InstantWinStateType.Value = InstantWinStateType.Open
                       )

  implicit object InstantWinStateType extends Enumeration {
    val Open: InstantWinStateType.Value = Value("OPEN")
    val Close: InstantWinStateType.Value = Value("CLOSE")

    val all = Seq(Open, Close)
  }

}
