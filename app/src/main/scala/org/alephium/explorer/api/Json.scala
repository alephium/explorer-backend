// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
