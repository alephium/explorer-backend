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

import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{Address, GroupIndex}
import org.alephium.protocol.vm.LockupScript
import org.alephium.protocol.model.AddressLike

object AddressUtil {

  def p2pkGroupAddress(
      address: Address
  )(implicit groupConfig: GroupConfig): Option[GroupIndex] = {
    address.lockupScript match {
      case p2pk: LockupScript.P2PK => Some(p2pk.groupIndex)
      case _                       => None
    }
  }

  def toRawAddress(address: Address): String = {
    address.lockupScript match {
      case p2pk: LockupScript.P2PK => p2pk.toBase58WithoutGroup
      case _                       => address.toBase58
    }
  }

  def getGroupIndex(
      address: AddressLike
  )(implicit groupConfig: GroupConfig): Option[GroupIndex] = {
    address.lockupScriptResult match {
      case LockupScript.CompleteLockupScript(lockupScript) =>
        lockupScript match {
          case p2pk: LockupScript.P2PK => Some(p2pk.groupIndex)
          case _                       => None
        }
      case _: LockupScript.HalfDecodedP2PK =>
        None
    }
  }

  def getAddress(address: AddressLike, groupIndexOption: Option[GroupIndex]): Address = {
    address.lockupScriptResult match {
      case LockupScript.CompleteLockupScript(lockupScript) =>
        Address.from(lockupScript)
      case LockupScript.HalfDecodedP2PK(publicKey) =>
        groupIndexOption match {
          case Some(groupIndex) =>
            Address.Asset(LockupScript.p2pk(publicKey, groupIndex))
          case None =>
            throw new Exception("Group index is required for half decoded P2PK")
        }
    }
  }
}
