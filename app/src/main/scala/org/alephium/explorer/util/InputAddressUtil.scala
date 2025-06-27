// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
    protocol.vm.UnlockScript.SameAsPrevious: protocol.vm.UnlockScript
  )
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
          case protocol.vm.UnlockScript.PoLW(pk) =>
            Some(protocol.model.Address.p2pkh(pk))
          case protocol.vm.UnlockScript.P2PK =>
            None
          case protocol.vm.UnlockScript.P2HMPK(_, _) =>
            None
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
  def addressFromProtocolInputs(inputs: ArraySeq[api.model.AssetInput]): Option[Address] = {
    if (inputs.isEmpty) {
      None
    } else {
      val addressOpt = inputs.headOption.flatMap(InputAddressUtil.addressFromProtocolInput)
      addressOpt match {
        case None => None
        case Some(_) =>
          if (
            inputs
              .drop(1)
              .forall(input =>
                input.unlockScript === sameAsPrevious || InputAddressUtil
                  .addressFromProtocolInput(input) === addressOpt
              )
          ) {
            addressOpt
          } else {
            None
          }
      }
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
  def convertSameAsPrevious(
      inputs: ArraySeq[api.model.AssetInput]
  ): ArraySeq[api.model.AssetInput] = {
    if (inputs.sizeIs <= 1) {
      inputs
    } else {
      var lastUncompressedScript: Int = -1
      val converted = inputs.view.zipWithIndex.map { case (input, index) =>
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
