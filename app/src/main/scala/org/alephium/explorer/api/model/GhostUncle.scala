// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.model.GhostUncleBlockEntry
import org.alephium.explorer.api.Codecs._
import org.alephium.json.Json._
import org.alephium.protocol.model.{Address, BlockHash}

final case class GhostUncle(blockHash: BlockHash, miner: Address.Asset) {
  def toProtocol(): GhostUncleBlockEntry = GhostUncleBlockEntry(blockHash, miner)
}

object GhostUncle {
  implicit val codec: ReadWriter[GhostUncle] = macroRW
}
