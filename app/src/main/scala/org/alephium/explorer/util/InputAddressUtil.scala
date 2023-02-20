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

import scala.collection.immutable.ArraySeq

import com.typesafe.scalalogging.StrictLogging

import org.alephium.api
import org.alephium.explorer.AnyOps
import org.alephium.protocol
import org.alephium.protocol.model.Address
import org.alephium.serde._

object InputAddressUtil extends StrictLogging {
  private val sameAsPrevious = serialize(
    protocol.vm.UnlockScript.SameAsPrevious: protocol.vm.UnlockScript)
  /*
   * Extract address from an [[org.alephium.api.model.AssetInput]]
   * Addresses can only be extracted from P2PKH and P2SH.
   * We can't find back the address from a P2MPKH
   */
  def addressFromProtocolInput(input: api.model.AssetInput): Option[Address] =
    input.toProtocol() match {
      case Right(value) =>
        value.unlockScript match {
          case protocol.vm.UnlockScript.P2PKH(pk) =>
            Some(protocol.model.Address.p2pkh(pk))
          case protocol.vm.UnlockScript.P2SH(script, _) =>
            val lockup = protocol.vm.LockupScript.p2sh(protocol.Hash.hash(script))
            Some(protocol.model.Address.from(lockup))
          case protocol.vm.UnlockScript.SameAsPrevious =>
            None
          case protocol.vm.UnlockScript.P2MPKH(_) =>
            None
        }
      case Left(error) =>
        logger.error(s"Cannot decode protocol input: $error")
        None
    }

  /*
   * Exctract a single address from multiple protocol inputs.
   * Addresses are extracted using `addressFromProtocolInput` function.
   * If every addresses are the same, we consider it as the correct address.
   * If no address or > 1 address are found, we return `None`
   */
  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def addressFromProtocolInputs(inputs: ArraySeq[api.model.AssetInput]): Option[Address] = {
    if (inputs.isEmpty) {
      None
    } else {
      val addressOpt = inputs.headOption.flatMap(InputAddressUtil.addressFromProtocolInput)
      addressOpt match {
        case None => None
        case Some(_) =>
          if (inputs.tail.forall(input =>
                input.unlockScript === sameAsPrevious || InputAddressUtil
                  .addressFromProtocolInput(input) === addressOpt)) {
            addressOpt
          } else {
            None
          }
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def convertSameAsPrevious(
      inputs: ArraySeq[api.model.AssetInput]): ArraySeq[api.model.AssetInput] = {
    if (inputs.sizeIs <= 1) {
      inputs
    } else {
      var lastUncompressedScript: Int = -1
      val converted = inputs.view.zipWithIndex.map {
        case (input, index) =>
          if (input.unlockScript === sameAsPrevious) {
            input.copy(unlockScript = inputs(lastUncompressedScript).unlockScript)
          } else {
            lastUncompressedScript = index
            input
          }
      }
      ArraySeq.from(converted)
    }
  }
}
