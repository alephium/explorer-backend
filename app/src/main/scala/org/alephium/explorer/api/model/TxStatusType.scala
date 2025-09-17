// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

sealed trait TxStatusType {
  def string: String
}

object TxStatusType {
  case object Conflicted extends TxStatusType {
    val string: String = "conflicted"
  }

  def validate(str: String): Option[TxStatusType] =
    str match {
      case Conflicted.string => Some(Conflicted)
      case _                 => None

    }
}
