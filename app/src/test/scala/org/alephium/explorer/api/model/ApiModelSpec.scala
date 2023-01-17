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

import java.time.Instant

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson._
import org.alephium.explorer.{AlephiumSpec, Hash}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.{Hex, TimeStamp}

class ApiModelSpec() extends AlephiumSpec {

  def check[T: Reader: Writer](data: T, jsonRaw: String) = {
    write(data) is jsonRaw.filterNot(_.isWhitespace)
    read[T](jsonRaw) is data
  }

  "IntervalType" in {
    check[IntervalType](IntervalType.Hourly, s""""hourly"""")
    check[IntervalType](IntervalType.Daily, s""""daily"""")
  }

  "Transaction" should {
    "convert to json" in {
      forAll(transactionGen) { tx =>
        val expected = s"""
       |{
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash.value.toHexString}",
       |  "timestamp": ${tx.timestamp.millis},
       |  "inputs": [],
       |  "outputs": [],
       |  "gasAmount": ${tx.gasAmount},
       |  "gasPrice": "${tx.gasPrice}"
       |}""".stripMargin
        check(tx, expected)
      }
    }
    "convert to csv" in {
      forAll(transactionGen) { tx =>
        //No inputs or outputs so no addresses nor amounts
        val expected =
          s"${tx.hash.toHexString},${tx.blockHash.toHexString},${tx.timestamp.millis},${Instant
            .ofEpochMilli(tx.timestamp.millis)},,,0,0\n"
        tx.toCsv(addressGen.sample.get) is expected
      }

      val address = Address.unsafe("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n")
      val transaction = Transaction(
        TransactionId
          .from(Hex.unsafe("798e9e137aec7c2d59d9655b4ffa640f301f628bf7c365083bb255f6aa5f89ef"))
          .get,
        BlockHash
          .from(Hex.unsafe("bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5"))
          .get,
        TimeStamp.unsafe(1636379973000L), // 2021-11-08T11:20:06+00:00,
        ArraySeq(
          Input(
            OutputRef(1, Hash.generate),
            unlockScript   = None,
            address        = Some(address),
            attoAlphAmount = Some(ALPH.alph(10)),
            tokens         = None
          )
        ),
        ArraySeq(
          AssetOutput(
            hint           = 0,
            key            = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address        = Address.unsafe("14PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE7w"),
            tokens         = None,
            lockTime       = None,
            message        = None,
            spent          = None
          ),
          AssetOutput(
            hint           = 0,
            key            = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address        = Address.unsafe("25PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE0a"),
            tokens         = None,
            lockTime       = None,
            message        = None,
            spent          = None
          ),
          AssetOutput(
            hint           = 0,
            key            = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address,
            tokens   = None,
            lockTime = None,
            message  = None,
            spent    = None
          )
        ),
        gasAmount = 1,
        gasPrice  = ALPH.alph(1)
      )

      val expected =
        s"798e9e137aec7c2d59d9655b4ffa640f301f628bf7c365083bb255f6aa5f89ef,bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5,1636379973000,2021-11-08T13:59:33Z,1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n,14PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE7w-25PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE0a,-2000000000000000000,-2\n"

      transaction.toCsv(address) is expected
    }
  }

  "ConfirmedTransaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
       |{
       |  "type": "Confirmed",
       |  "hash": "${tx.hash.value.toHexString}",
       |  "blockHash": "${tx.blockHash.value.toHexString}",
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
     |  ${input.unlockScript.map(script => s""","unlockScript": ${write(script)}""").getOrElse("")}
     |  ${input.txHashRef.map(txHashRef      => s""","txHashRef": "${txHashRef.toHexString}"""")
                          .getOrElse("")}
     |  ${input.address.map(address           => s""","address": "${address}"""").getOrElse("")}
     |  ${input.attoAlphAmount
                          .map(attoAlphAmount => s""","attoAlphAmount": "${attoAlphAmount}"""")
                          .getOrElse("")}
     |}""".stripMargin
      check(input, expected)
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
     |  "gasPrice": "${utx.gasPrice}",
     |  "lastSeen": ${utx.lastSeen.millis}
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
    forAll(groupSettingGen.flatMap(blockEntryGen(_))) { block =>
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
