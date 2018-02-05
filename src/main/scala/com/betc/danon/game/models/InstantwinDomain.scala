package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.PrizeDomain.Prize

object InstantwinDomain {

  case class Instantwin(
                         id: UUID,
                         game_id: UUID,
                         gameprize_id: UUID,
                         prize_id: UUID,
                         activate_date: Instant
                       )

  case class InstantwinExtended(
                                 id: UUID,
                                 game_id: UUID,
                                 gameprize_id: UUID,
                                 activate_date: Instant,
                                 prize: Prize
                               ) {
    def this(instantwin: Instantwin, prize: Prize) = this(id = instantwin.id, game_id = instantwin.game_id, gameprize_id = instantwin.gameprize_id, activate_date = instantwin.activate_date, prize = prize)
  }

}
