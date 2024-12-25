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
