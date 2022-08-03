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

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson._
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.json.Json._

class ApiModelSpec() extends AlephiumSpec {

  def check[T: Reader: Writer](data: T, jsonRaw: String) = {
    write(data) is jsonRaw.filterNot(_.isWhitespace)
    read[T](jsonRaw) is data
  }

  "IntervalType" in {
    check[IntervalType](IntervalType.Hourly, s""""hourly"""")
    check[IntervalType](IntervalType.Daily, s""""daily"""")
  }

  "Transaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "gasAmount": ${tx.gasAmount},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
      check(tx, expected)
    }
  }

  "ConfirmedTransaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "type": "Confirmed",
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "gasAmount": ${tx.gasAmount},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
      check(ConfirmedTransaction.from(tx), expected)
    }
  }

  "Output.Ref" in {
    forAll(outputRefGen) { outputRef =>
      val expected = s"""
     |{
     |  "hint": ${outputRef.hint},
     |  "key": "${outputRef.key.toHexString}"
     |}""".stripMargin
      check(outputRef, expected)
    }
  }

  "Token" in {
    forAll(tokenGen) { token =>
      val expected = s"""
     |{
     |  "id": "${token.id.toHexString}",
     |  "amount": "${token.amount}"
     |}""".stripMargin
      check(token, expected)
    }
  }

  "AsetOutput" in {
    forAll(assetOutputGen) { output =>
      val expected = s"""
     |{
     |  "type": "AssetOutput",
     |  "hint": ${output.hint},
     |  "key": "${output.key.toHexString}",
     |  "attoAlphAmount": "${output.attoAlphAmount}",
     |  "address": "${output.address}"
     |  ${output.tokens.map(tokens => s""","tokens": ${write(tokens)}""").getOrElse("")}
     |  ${output.lockTime.map(lockTime => s""","lockTime": ${lockTime.millis}""").getOrElse("")}
     |  ${output.message.map(message => s""","message":${write(message)}""")
                          .getOrElse("")}
     |  ${output.spent.map(spent       => s""","spent": "${spent.value.toHexString}"""").getOrElse("")}
     |}""".stripMargin
      check(output, expected)
    }
  }

  "ContractOutputGen" in {
    forAll(contractOutputGen) { output =>
      val expected = s"""
     |{
     |  "type": "ContractOutput",
     |  "hint": ${output.hint},
     |  "key": "${output.key.toHexString}",
     |  "attoAlphAmount": "${output.attoAlphAmount}",
     |  "address": "${output.address}"
     |  ${output.tokens.map(tokens => s""","tokens": ${write(tokens)}""").getOrElse("")}
     |  ${output.spent.map(spent => s""","spent": "${spent.value.toHexString}"""").getOrElse("")}
     |}""".stripMargin
      check(output, expected)
    }
  }

  "Input" in {
    forAll(inputGen) { input =>
      val expected = s"""
     |{
     |  "outputRef": ${write(input.outputRef)}
     |  ${input.unlockScript.map(script => s""","unlockScript": "${script}"""").getOrElse("")}
     |  ${input.address.map(address => s""","address": "${address}"""").getOrElse("")}
     |  ${input.attoAlphAmount
                          .map(attoAlphAmount => s""","attoAlphAmount": "${attoAlphAmount}"""")
                          .getOrElse("")}
     |}""".stripMargin
      check(input, expected)
    }
  }

  "UInput" in {
    forAll(uinputGen) { uinput =>
      val expected = uinput.unlockScript match {
        case None =>
          s"""
          |{
          |  "outputRef": ${write(uinput.outputRef)}
          |}""".stripMargin
        case Some(unlockScript) =>
          s"""
          |{
          |  "outputRef": ${write(uinput.outputRef)},
          |  "unlockScript": "${unlockScript}"
          |}""".stripMargin
      }
      check(uinput, expected)
    }
  }

  "UnconfirmedTx" in {
    forAll(utransactionGen) { utx =>
      val expected = s"""
     |{
     |  "type": "Unconfirmed",
     |  "hash": "${utx.hash.value.toHexString}",
     |  "chainFrom": ${utx.chainFrom.value},
     |  "chainTo": ${utx.chainTo.value},
     |  "inputs": ${write(utx.inputs)},
     |  "outputs": ${write(utx.outputs)},
     |  "gasAmount": ${utx.gasAmount},
     |  "gasPrice": "${utx.gasPrice}"
     |}""".stripMargin
      check(utx, expected)
    }
  }

  "BlockEntryLite" in {
    forAll(blockEntryLiteGen) { block =>
      val expected = s"""
       |{
       |  "hash": "${block.hash.value.toHexString}",
       |  "timestamp": ${block.timestamp.millis},
       |  "chainFrom": ${block.chainFrom.value},
       |  "chainTo": ${block.chainTo.value},
       |  "height": ${block.height.value},
       |  "txNumber": ${block.txNumber},
       |  "mainChain": ${block.mainChain},
       |  "hashRate": "${block.hashRate}"
       |}""".stripMargin
      check(block, expected)
    }
  }

  "BlockEntry" in {
    forAll(blockEntryGen(groupSettingGen.sample.get)) { block =>
      val expected = s"""
       |{
       |  "hash": "${block.hash.value.toHexString}",
       |  "timestamp": ${block.timestamp.millis},
       |  "chainFrom": ${block.chainFrom.value},
       |  "chainTo": ${block.chainTo.value},
       |  "height": ${block.height.value},
       |  "deps": ${write(block.deps)},
       |  "transactions": ${write(block.transactions)},
       |  "mainChain": ${block.mainChain},
       |  "hashRate": "${block.hashRate}"
       |}""".stripMargin
      check(block, expected)
    }
  }

  "TokenSupply" in {
    forAll(tokenSupplyGen) { tokenSupply =>
      val expected = s"""
       |{
       | "timestamp": ${tokenSupply.timestamp.millis},
       | "total": "${tokenSupply.total}",
       | "circulating": "${tokenSupply.circulating}",
       | "reserved": "${tokenSupply.reserved}",
       | "locked": "${tokenSupply.locked}",
       | "maximum": "${tokenSupply.maximum}"
       |}""".stripMargin
      check(tokenSupply, expected)
    }
  }

  "AddressInfo" in {
    forAll(addressInfoGen) { addressInfo =>
      val expected = s"""
       |{
       | "balance": "${addressInfo.balance}",
       | "lockedBalance": "${addressInfo.lockedBalance}",
       | "txNumber": ${addressInfo.txNumber}
       |}""".stripMargin
      check(addressInfo, expected)
    }
  }

  "ListBlocks" in {
    forAll(listBlocksGen) { listBlocks =>
      val expected = s"""
       |{
       | "total": ${listBlocks.total},
       | "blocks": ${write(listBlocks.blocks)}
       |}""".stripMargin
      check(listBlocks, expected)
    }
  }

  "ExplorerInfo" in {
    val expected = s"""
     |{
     |  "releaseVersion": "1.2.3",
     |  "commit": "b96f64ff"
     |}""".stripMargin
    check(ExplorerInfo("1.2.3", "b96f64ff"), expected)
  }
}
