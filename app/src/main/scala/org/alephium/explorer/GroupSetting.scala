// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.ChainIndex

object GroupSetting {

  def apply(groupNum: Int): GroupSetting = {
    implicit val groupConfig: GroupConfig = new GroupConfig { val groups = groupNum }

    val chainIndexes: ArraySeq[ChainIndex] = ArraySeq.from(for {
      i <- 0 until groupNum
      j <- 0 until groupNum
    } yield ChainIndex.unsafe(i, j))

    new GroupSetting(
      groupConfig = groupConfig,
      chainIndexes = chainIndexes
    )
  }
}

/** Stores group related settings */
class GroupSetting private (val groupConfig: GroupConfig, val chainIndexes: ArraySeq[ChainIndex]) {
  def groupNum: Int = groupConfig.groups
}
