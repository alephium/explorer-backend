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

import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers

import org.alephium.api
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.Generators._
import org.alephium.protocol
import org.alephium.serde._

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
class InputAddressUtilSpec extends AlephiumSpec with Matchers {

  "addressFromProtocolInput" should {
    "convert P2PKH input into the right address" in {
      forAll(unlockScriptProtocolP2PKHGen, outputRefProtocolGen) {
        case (p2pkh, outputRef) =>
          val assetInput = buildAssetInput(outputRef, p2pkh)

          val result = InputAddressUtil
            .addressFromProtocolInput(assetInput)

          result.nonEmpty is true

          result.get.toString is protocol.model.Address.p2pkh(p2pkh.publicKey).toBase58
      }
    }

    "convert P2SH input into the right address" in {
      forAll(unlockScriptProtocolP2SHGen, outputRefProtocolGen) {
        case (p2sh, outputRef) =>
          val assetInput = buildAssetInput(outputRef, p2sh)

          val result = InputAddressUtil
            .addressFromProtocolInput(assetInput)

          result.nonEmpty is true

          val lockup = protocol.vm.LockupScript.p2sh(protocol.Hash.hash(p2sh.script))
          result.get.toString is protocol.model.Address.from(lockup).toBase58
      }
    }

    "fail to convert P2MPKH input into an address" in {
      forAll(unlockScriptProtocolP2MPKHGen, outputRefProtocolGen) {
        case (p2mpkhUnlock, outputRef) =>
          val assetInput = buildAssetInput(outputRef, p2mpkhUnlock)

          InputAddressUtil
            .addressFromProtocolInput(assetInput) is None
      }
    }
  }

  "addressFromProtocolInputs" should {
    "return None if list is empty" in {
      InputAddressUtil.addressFromProtocolInputs(ArraySeq.empty) is None
    }

    "return a single address if all inputs have the same p2pkh address" in {
      forAll(Gen.nonEmptyListOf(outputRefProtocolGen)) { outputRefs =>
        val p2pkh       = unlockScriptProtocolP2PKHGen.sample.get
        val assetInputs = outputRefs.map(buildAssetInput(_, p2pkh))

        val result = InputAddressUtil.addressFromProtocolInputs(assetInputs)

        result.get.toString is protocol.model.Address.p2pkh(p2pkh.publicKey).toBase58
      }
    }

    "return None if > 1 address" in {
      forAll(Gen.nonEmptyListOf(outputRefProtocolGen), outputRefProtocolGen) {
        case (outputRefs, outputRef) =>
          val p2pkh       = unlockScriptProtocolP2PKHGen.sample.get
          val assetInputs = outputRefs.map(buildAssetInput(_, p2pkh))

          val otherP2pkh      = unlockScriptProtocolP2PKHGen.sample.get
          val otherAssetInput = buildAssetInput(outputRef, otherP2pkh)

          val result = InputAddressUtil.addressFromProtocolInputs(otherAssetInput +: assetInputs)

          result is None
      }
    }

    "return None if one of the address isn't extractable" in {
      forAll(Gen.nonEmptyListOf(outputRefProtocolGen), outputRefProtocolGen) {
        case (outputRefs, outputRef) =>
          val p2pkh       = unlockScriptProtocolP2PKHGen.sample.get
          val assetInputs = outputRefs.map(buildAssetInput(_, p2pkh))

          val p2mpkh          = unlockScriptProtocolP2MPKHGen.sample.get
          val otherAssetInput = buildAssetInput(outputRef, p2mpkh)

          val result = InputAddressUtil.addressFromProtocolInputs(otherAssetInput +: assetInputs)

          result is None
      }
    }
  }

  "convertSameAsPrevious" should {
    "return empty list if list is empty" in {
      InputAddressUtil.convertSameAsPrevious(ArraySeq.empty) is ArraySeq.empty
    }

    "return same list if list as only one element" in {
      forAll(inputProtocolGen) { input =>
        InputAddressUtil.convertSameAsPrevious(ArraySeq(input)) is ArraySeq(input)
      }
    }

    "correcly convert simple SameAsPrevious case" in {
      val sameAsPrevious =
        serialize(protocol.vm.UnlockScript.SameAsPrevious: protocol.vm.UnlockScript)
      forAll(inputProtocolGen, inputProtocolGen, inputProtocolGen) {
        case (input1, in2, in3) =>
          val input2 = in2.copy(unlockScript = sameAsPrevious)
          val input3 = in3.copy(unlockScript = sameAsPrevious)

          val result = InputAddressUtil.convertSameAsPrevious(ArraySeq(input1, input2, input3))

          val newInput2 = input2.copy(unlockScript = input1.unlockScript)
          val newInput3 = input3.copy(unlockScript = input1.unlockScript)

          result is ArraySeq(input1, newInput2, newInput3)
      }
    }

    "correcly convert slightly more complex SameAsPrevious case" in {
      val sameAsPrevious =
        serialize(protocol.vm.UnlockScript.SameAsPrevious: protocol.vm.UnlockScript)
      forAll(inputProtocolGen, inputProtocolGen, inputProtocolGen, inputProtocolGen) {
        case (input1, in2, input3, in4) =>
          val input2 = in2.copy(unlockScript = sameAsPrevious)
          val input4 = in4.copy(unlockScript = sameAsPrevious)

          val result =
            InputAddressUtil.convertSameAsPrevious(ArraySeq(input1, input2, input3, input4))

          val newInput2 = input2.copy(unlockScript = input1.unlockScript)
          val newInput4 = input4.copy(unlockScript = input3.unlockScript)

          result is ArraySeq(input1, newInput2, input3, newInput4)
      }
    }
  }

  def buildAssetInput(outputRef: api.model.OutputRef, unlockScript: protocol.vm.UnlockScript) = {
    api.model.AssetInput(
      outputRef,
      serialize(unlockScript)
    )
  }
}
