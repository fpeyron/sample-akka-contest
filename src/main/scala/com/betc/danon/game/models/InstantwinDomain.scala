package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.PrizeDomain.Prize

object InstantwinDomain {

  case class Instantwin(
                         id: UUID,
                         gameId: UUID,
                         gamePrizeId: UUID,
                         prizeId: UUID,
                         activateDate: Instant
                       )

  case class InstantwinExtended(
                                 id: UUID,
                                 gameId: UUID,
                                 gamePrizeId: UUID,
                                 activateDate: Instant,
                                 prize: Prize
                               ) {
    def this(instantwin: Instantwin, prize: Prize) = this(id = instantwin.id, gameId = instantwin.gameId, gamePrizeId = instantwin.gamePrizeId, activateDate = instantwin.activateDate, prize = prize)
  }

}
