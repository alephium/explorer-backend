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

import org.alephium.util.{Duration, TimeStamp}

object Consensus {
  // TODO Add this to config
  // scalastyle:off magic.number
  val rhoneHardForkTimestamp: TimeStamp  = TimeStamp.unsafe(1718186400000L)
  val danubeHardForkTimestamp: TimeStamp = TimeStamp.unsafe(9000000000000000000L)

  def blockTargetTime(timestamp: TimeStamp): Duration =
    if (timestamp.isBefore(rhoneHardForkTimestamp)) {
      Duration.ofSecondsUnsafe(64)
    } else {
      Duration.ofSecondsUnsafe(16)
    }
  // scalastyle:on magic.number
}
