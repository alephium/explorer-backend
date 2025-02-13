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

package org.alephium.explorer.api

import org.alephium.api.ApiModelCodec
import org.alephium.explorer.config.Default
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
object Json extends ApiModelCodec {

  implicit val groupConfig: GroupConfig = Default.groupConfig

  implicit val u256ReadWriter: ReadWriter[U256] = ReadWriter.join(u256Reader, u256Writer)
  implicit val groupIndexReadWriter: ReadWriter[GroupIndex] =
    readwriter[Int].bimap(
      _.value,
      group => new GroupIndex(group)
    )
}
