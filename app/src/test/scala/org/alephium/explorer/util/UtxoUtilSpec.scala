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

import scala.util.Random

import org.scalacheck.Gen

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.api.model.{AssetOutput, ContractOutput, Output}
import org.alephium.util.U256

class UtxoUtilSpec extends AlephiumSpec {

  "UtxoUtil.amountForAddressInInputs" should {
    "return amount in inputs" when {
      "all inputs belong to the same address" in {
        forAll(addressGen, Gen.listOf(inputGen)) {
          case (address, inputs) =>
            val addressInputs = inputs.map(_.copy(address = Some(address)))
            val amount = inputs.foldLeft(Option(U256.Zero)) {
              case (acc, in) => acc.flatMap(_.add(in.attoAlphAmount.getOrElse(U256.Zero)))
            }

            UtxoUtil.amountForAddressInInputs(address, addressInputs) is amount
        }
      }
      "part of the inputs belong to the address" in {
        forAll(addressGen, addressGen, Gen.listOf(inputGen)) {
          case (address, address2, inputs) =>
            val addresses       = Seq(address, address2)
            val addressesInputs = inputs.map(_.copy(address = Random.shuffle(addresses).headOption))

            val amount =
              addressesInputs.filter(_.address == Some(address)).foldLeft(Option(U256.Zero)) {
                case (acc, in) => acc.flatMap(_.add(in.attoAlphAmount.getOrElse(U256.Zero)))
              }

            UtxoUtil.amountForAddressInInputs(address, addressesInputs) is amount
        }
      }
    }

    "return zero" when {
      "address doesn't belong to inputs" in {
        forAll(addressGen, Gen.listOf(inputGen)) {
          case (address, inputs) =>
            UtxoUtil.amountForAddressInInputs(address, inputs) is Some(U256.Zero)
        }
      }
    }
  }

  "UtxoUtil.amountForAddressInOutputs" should {
    "return amount in outputs" when {
      "outputs contains the address" in {
        forAll(addressGen, addressGen, Gen.listOf(outputGen)) {
          case (address, address2, outputs) =>
            val addresses = Seq(address, address2)
            val addressesOutputs = outputs.map {
              case asset: AssetOutput =>
                asset.copy(address = Random.shuffle(addresses).head): Output
              case contract: ContractOutput =>
                contract.copy(address = Random.shuffle(addresses).head): Output
            }

            val amount = addressesOutputs.filter(_.address == address).foldLeft(Option(U256.Zero)) {
              case (acc, in) => acc.flatMap(_.add(in.attoAlphAmount))
            }

            UtxoUtil.amountForAddressInOutputs(address, addressesOutputs) is amount
        }
      }
    }

    "return zero" when {
      "address doesn't belong to inputs" in {
        forAll(addressGen, Gen.listOf(outputGen)) {
          case (address, outputs) =>
            UtxoUtil.amountForAddressInOutputs(address, outputs) is Some(U256.Zero)
        }
      }
    }
  }

  "UtxoUtil.deltaAmountForAddress" should {
    "return correct delta" in {
      forAll(addressGen, Gen.listOf(inputGen), Gen.listOf(outputGen)) {
        case (address, inputs, outputs) =>
          val addressInputs = inputs.map(_.copy(address = Some(address)))
          val addressOutputs = outputs.map {
            case asset: AssetOutput =>
              asset.copy(address = address): Output
            case contract: ContractOutput =>
              contract.copy(address = address): Output
          }
          val inputAmount = inputs
            .foldLeft(Option(U256.Zero)) {
              case (acc, in) => acc.flatMap(_.add(in.attoAlphAmount.getOrElse(U256.Zero)))
            }
            .get
          val outputAmount = outputs
            .foldLeft(Option(U256.Zero)) {
              case (acc, in) => acc.flatMap(_.add(in.attoAlphAmount))
            }
            .get

          val expected = outputAmount.v.subtract(inputAmount.v)

          val delta = UtxoUtil.deltaAmountForAddress(address, addressInputs, addressOutputs).get

          delta is expected

          val signum = delta.signum()
          if (outputAmount > inputAmount) {
            signum is 1
          } else if (outputAmount < inputAmount) {
            signum is -1
          } else {
            signum is 0
          }
      }
    }
  }
}
