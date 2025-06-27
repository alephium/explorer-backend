// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.persistence.model.GrouplessAddress
import org.alephium.protocol.model.Address
import org.alephium.protocol.vm.LockupScript

object AddressUtil {

  def convertToGrouplessAddress(address: Address): Option[GrouplessAddress] =
    address match {
      case Address.Asset(lockup) =>
        lockup match {
          case LockupScript.P2PK(pk, _) =>
            Some(GrouplessAddress(ApiAddress.HalfDecodedP2PK(pk)))
          case LockupScript.P2HMPK(hash, _) =>
            Some(GrouplessAddress(ApiAddress.HalfDecodedP2HMPK(hash)))
          case _ => None
        }
      case _ => None
    }
}
