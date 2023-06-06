// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

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
      groupConfig  = groupConfig,
      chainIndexes = chainIndexes
    )
  }
}

/** Stores group related settings */
class GroupSetting private (val groupConfig: GroupConfig, val chainIndexes: ArraySeq[ChainIndex]) {
  def groupNum: Int = groupConfig.groups
}
