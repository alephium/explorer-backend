package org.alephium.explorer.api.model

import org.alephium.util.TimeStamp

final case class TimeInterval(from: TimeStamp, to: TimeStamp)

object TimeInterval {
  def validate(from: TimeStamp, to: TimeStamp): Either[String, TimeInterval] =
    if (to < from) {
      Left("`to` cannot be before `from`")
    } else {
      Right(TimeInterval(from, to))
    }
}
