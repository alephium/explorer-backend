// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.Address

final case class SubContracts(
    subContracts: ArraySeq[Address]
)

object SubContracts {
  implicit val readWriter: ReadWriter[SubContracts] = macroRW
}
