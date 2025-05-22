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

package org.alephium.explorer.util

import org.alephium.protocol.model.{Address, AddressLike}
import org.alephium.protocol.vm.LockupScript

object AddressUtil {

  def convertToGrouplessAddress(address: Address): Option[AddressLike] =
    address match {
      case Address.Asset(lockup) =>
        lockup match {
          case LockupScript.P2PK(pk, _) =>
            Some(AddressLike(LockupScript.HalfDecodedP2PK(pk)))
          case _ => None
        }
      case _ => None
    }
}
