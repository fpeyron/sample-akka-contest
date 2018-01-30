package fr.sysf.sample

import java.util.UUID

object ActorUtil {

  def string2UUID(name: String): Option[UUID] = try {
    Some(UUID.fromString(name))
  } catch {
    case _: Throwable => None
  }
}
