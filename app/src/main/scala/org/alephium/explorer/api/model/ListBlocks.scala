// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.json.Json._

final case class ListBlocks(total: Int, blocks: ArraySeq[BlockEntryLite])

object ListBlocks {
  implicit val readWriter: ReadWriter[ListBlocks] = macroRW
}
