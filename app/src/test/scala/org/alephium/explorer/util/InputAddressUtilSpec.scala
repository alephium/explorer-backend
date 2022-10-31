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

  implicit val groupSetting = groupSettingGen.sample.get

  "addressFromProtocolInput" should {
    "convert P2PKH input into the right address" in new Fixture {
      forAll(unlockScriptProtocolP2PKHGen, outputRefProtocolGen) {
        case (p2pkh, outputRef) =>
          val assetInput = buildAssetInput(outputRef, p2pkh)

          val result = InputAddressUtil
            .addressFromProtocolInput(assetInput)

          result.nonEmpty is true

          result.get.toString is protocol.model.Address.p2pkh(p2pkh.publicKey).toBase58
      }
    }

    "convert P2SH input into the right address" in new Fixture {
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

    "fail to convert P2MPKH input into an address" in new Fixture {
      forAll(unlockScriptProtocolP2MPKHGen, outputRefProtocolGen) {
        case (p2mpkhUnlock, outputRef) =>
          val assetInput = buildAssetInput(outputRef, p2mpkhUnlock)

          InputAddressUtil
            .addressFromProtocolInput(assetInput) is None
      }
    }
  }

  "addressFromProtocolInputs" should {
    "return None if list is empty" in new Fixture {
      InputAddressUtil.addressFromProtocolInputs(ArraySeq.empty) is None
    }

    "return a single address if all inputs have the same p2pkh address" in new Fixture {
      forAll(Gen.nonEmptyListOf(outputRefProtocolGen)) { outputRefs =>
        val p2pkh       = unlockScriptProtocolP2PKHGen.sample.get
        val assetInputs = outputRefs.map(buildAssetInput(_, p2pkh))

        val result = InputAddressUtil.addressFromProtocolInputs(assetInputs)

        result.get.toString is protocol.model.Address.p2pkh(p2pkh.publicKey).toBase58
      }
    }

    "return None if > 1 address" in new Fixture {
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

    "return None if one of the address isn't extractable" in new Fixture {
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

  trait Fixture {
    def buildAssetInput(outputRef: api.model.OutputRef, unlockScript: protocol.vm.UnlockScript) = {
      api.model.AssetInput(
        outputRef,
        serialize(unlockScript)
      )
    }
  }
}
