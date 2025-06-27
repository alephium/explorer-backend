// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import upickle.core.Abort

import org.alephium.json.Json._
import org.alephium.protocol.ALPH

final class Height(val value: Int) extends AnyVal {
  override def toString(): String = value.toString
}

object Height {
  def unsafe(value: Int): Height = new Height(value)
  def from(value: Int): Either[String, Height] =
    if (value < 0) {
      Left(s"height cannot be negative ($value)")
    } else {
      Right(Height.unsafe(value))
    }

  val genesis: Height = Height.unsafe(ALPH.GenesisHeight)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val readWriter: ReadWriter[Height] =
    readwriter[Int].bimap[Height](
      _.value,
      int =>
        from(int) match {
          case Right(height) => height
          case Left(error)   => throw new Abort(error)
        }
    )

  implicit val ordering: Ordering[Height] = Ordering.by[Height, Int](_.value)
}
