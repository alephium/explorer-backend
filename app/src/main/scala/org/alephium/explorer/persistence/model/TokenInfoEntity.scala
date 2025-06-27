// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TokenId
import org.alephium.util.TimeStamp

final case class TokenInfoEntity(
    token: TokenId,
    lastUsed: TimeStamp,
    category: Option[String],
    interfaceId: Option[InterfaceIdEntity]
) {
  def toApi(): TokenInfo = {
    val stdInterfaceId =
      interfaceId.map(
        _.toApi match {
          case tokenInterface: TokenStdInterfaceId => tokenInterface
          case other                               => StdInterfaceId.Unknown(other.id)
        }
      )
    TokenInfo(
      token,
      stdInterfaceId,
      stdInterfaceId.map(_.id),
      stdInterfaceId.map(_.category)
    )
  }
}
