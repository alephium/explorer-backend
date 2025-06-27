// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.protocol.config.GroupConfig

final case class GrouplessAddress(
    lockupScript: ApiAddress.HalfDecodedLockupScript
) {

  def toBase58(implicit config: GroupConfig): String = ApiAddress(lockupScript).toBase58
}
