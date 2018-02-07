package com.betc.danon.game.models

import java.time.Instant

trait Event {
  def timestamp: Instant
}
