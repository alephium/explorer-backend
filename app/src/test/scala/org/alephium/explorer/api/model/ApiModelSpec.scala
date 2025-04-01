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

import org.scalacheck.Gen

import org.alephium.api.UtilJson._
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.{ALPH, Hash}
import org.alephium.protocol.model.{Address, AddressLike, BlockHash, TransactionId}
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
                          |  "outputs": ${write(tx.outputs)},
                          |  "version": ${tx.version},
                          |  "networkId": ${tx.networkId},
                          |  "scriptOpt": ${tx.scriptOpt.map(s => s""""$s"""").getOrElse("null")},
                          |  "gasAmount": ${tx.gasAmount},
                          |  "gasPrice": "${tx.gasPrice}",
                          |  "scriptExecutionOk": ${tx.scriptExecutionOk},
                          |  "inputSignatures": ${write(tx.inputSignatures)},
                          |  "scriptSignatures": ${write(tx.scriptSignatures)},
                          |  "coinbase": ${tx.coinbase}
                          |}""".stripMargin
        check(tx, expected)
      }
    }
    "convert to csv" in {
      forAll(transactionGen) { tx =>
        // No inputs or outputs so no addresses nor amounts
        val expected =
          s"${tx.hash.toHexString},${tx.blockHash.toHexString},${tx.timestamp.millis},${Instant
              .ofEpochMilli(tx.timestamp.millis)},,${tx.outputs.map(_.address).mkString("-")},0,0\n"
        tx.toCsv(addressLikeGen.sample.get)
        expected
      }

      val address     = Address.fromBase58("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n").get
      val addressLike = AddressLike.fromBase58("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n").get

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
            unlockScript = None,
            address = Some(address),
            attoAlphAmount = Some(ALPH.alph(10)),
            tokens = None,
            contractInput = false
          )
        ),
        ArraySeq(
          AssetOutput(
            hint = 0,
            key = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address = Address.fromBase58("14PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE7w").get,
            tokens = None,
            lockTime = None,
            message = None,
            spent = None,
            fixedOutput = true
          ),
          AssetOutput(
            hint = 0,
            key = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address = Address.fromBase58("22fnZLkZJUSyhXgboirmJktWkEBRk1pV8L6gfpc53hvVM").get,
            tokens = None,
            lockTime = None,
            message = None,
            spent = None,
            fixedOutput = true
          ),
          AssetOutput(
            hint = 0,
            key = Hash.generate,
            attoAlphAmount = ALPH.alph(8),
            address,
            tokens = None,
            lockTime = None,
            message = None,
            spent = None,
            fixedOutput = true
          )
        ),
        version = 0,
        networkId = 0,
        scriptOpt = None,
        gasAmount = 1,
        gasPrice = ALPH.alph(1),
        false,
        ArraySeq.empty,
        ArraySeq.empty,
        true
      )

      val expected =
        s"798e9e137aec7c2d59d9655b4ffa640f301f628bf7c365083bb255f6aa5f89ef,bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5,1636379973000,2021-11-08T13:59:33Z,1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n,14PqtYSSbwpUi2RJKUvv9yUwGafd6yHbEcke7ionuiE7w-22fnZLkZJUSyhXgboirmJktWkEBRk1pV8L6gfpc53hvVM,-2000000000000000000,-2\n"

      transaction.toCsv(addressLike) is expected
    }
  }

  "AcceptedTransaction" in {
    forAll(transactionGen) { tx =>
      val expected = s"""
                        |{
                        |  "type": "Accepted",
                        |  "hash": "${tx.hash.value.toHexString}",
                        |  "blockHash": "${tx.blockHash.value.toHexString}",
                        |  "timestamp": ${tx.timestamp.millis},
                        |  "inputs": ${write(tx.inputs)},
                        |  "outputs": ${write(tx.outputs)},
                        |  "version": ${tx.version},
                        |  "networkId": ${tx.networkId},
                        |  "scriptOpt": ${tx.scriptOpt.map(s => s""""$s"""").getOrElse("null")},
                        |  "gasAmount": ${tx.gasAmount},
                        |  "gasPrice": "${tx.gasPrice}",
                        |  "scriptExecutionOk": ${tx.scriptExecutionOk},
                        |  "inputSignatures": ${write(tx.inputSignatures)},
                        |  "scriptSignatures": ${write(tx.scriptSignatures)},
                        |  "coinbase": ${tx.coinbase}
                        |}""".stripMargin
      check(AcceptedTransaction.from(tx), expected)
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

  "AssetOutput" in {
    forAll(assetOutputGen) { output =>
      val expected =
        s"""
           |{
           |  "type": "AssetOutput",
           |  "hint": ${output.hint},
           |  "key": "${output.key.toHexString}",
           |  "attoAlphAmount": "${output.attoAlphAmount}",
           |  "address": "${output.address}"
           |  ${output.tokens.map(tokens => s""","tokens": ${write(tokens)}""").getOrElse("")}
           |  ${output.lockTime
            .map(lockTime => s""","lockTime": ${lockTime.millis}""")
            .getOrElse("")}
           |  ${output.message
            .map(message => s""","message":${write(message)}""")
            .getOrElse("")}
           |  ${output.spent
            .map(spent => s""","spent": "${spent.value.toHexString}"""")
            .getOrElse("")}
           |  ,"fixedOutput": ${output.fixedOutput}
           |}""".stripMargin
      check(output, expected)
    }
  }

  "ContractOutputGen" in {
    forAll(contractOutputGen) { output =>
      val expected =
        s"""
           |{
           |  "type": "ContractOutput",
           |  "hint": ${output.hint},
           |  "key": "${output.key.toHexString}",
           |  "attoAlphAmount": "${output.attoAlphAmount}",
           |  "address": "${output.address}"
           |  ${output.tokens.map(tokens => s""","tokens": ${write(tokens)}""").getOrElse("")}
           |  ${output.spent
            .map(spent => s""","spent": "${spent.value.toHexString}"""")
            .getOrElse("")}
           |  ,"fixedOutput": ${output.fixedOutput}
           |}""".stripMargin
      check(output, expected)
    }
  }

  "Input" in {
    forAll(inputGen) { input =>
      val expected =
        s"""
           |{
           |  "outputRef": ${write(input.outputRef)}
           |  ${input.unlockScript
            .map(script => s""","unlockScript": ${write(script)}""")
            .getOrElse("")}
           |  ${input.txHashRef
            .map(txHashRef => s""","txHashRef": "${txHashRef.toHexString}"""")
            .getOrElse("")}
           |  ${input.address.map(address => s""","address": "${address}"""").getOrElse("")}
           |  ${input.attoAlphAmount
            .map(attoAlphAmount => s""","attoAlphAmount": "${attoAlphAmount}"""")
            .getOrElse("")}
           |  ${input.tokens
            .map(tokens => s""","tokens": ${write(tokens)}""")
            .getOrElse("")}
           |  ,"contractInput": ${input.contractInput}
           |}""".stripMargin
      check(input, expected)
    }
  }

  "MempoolTransaction" in {
    forAll(mempooltransactionGen) { utx =>
      val expected = s"""
                        |{
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

  "PendingTransaction" in {
    forAll(mempooltransactionGen) { utx =>
      val expected = s"""
                        |{
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
                        |  "nonce": ${write(block.nonce)},
                        |  "version": ${block.version},
                        |  "depStateHash": "${block.depStateHash.toHexString}",
                        |  "txsHash": "${block.txsHash.toHexString}",
                        |  "txNumber": ${block.txNumber},
                        |  "target": ${write(block.target)},
                        |  "hashRate": ${write(block.hashRate)},
                        |  "parent": ${if (block.parent.isDefined) {
                         s""""${block.parent.get.toHexString}""""
                       } else { "null" }},
                        |  "mainChain": ${block.mainChain},
                        |  "ghostUncles": ${write(block.ghostUncles)}
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

  "AddressBalance" in {
    forAll(addressBalanceGen) { addressBalance =>
      val expected = s"""
                        |{
                        | "balance": "${addressBalance.balance}",
                        | "lockedBalance": "${addressBalance.lockedBalance}"
                        |}""".stripMargin
      check(addressBalance, expected)
    }
  }

  "AddressTokenBalance" in {
    forAll(addressTokenBalanceGen) { addressTokenBalance =>
      val expected = s"""
                        |{
                        | "tokenId": "${addressTokenBalance.tokenId.toHexString}",
                        | "balance": "${addressTokenBalance.balance}",
                        | "lockedBalance": "${addressTokenBalance.lockedBalance}"
                        |}""".stripMargin
      check(addressTokenBalance, expected)
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
                      |  "commit": "b96f64ff",
                      |  "migrationsVersion": 0,
                      |  "lastFinalizedInputTime": 1234,
                      |  "lastHoldersUpdate": 1234
                      |}""".stripMargin
    check(
      ExplorerInfo("1.2.3", "b96f64ff", 0, TimeStamp.unsafe(1234), TimeStamp.unsafe(1234)),
      expected
    )
  }

  "ContractParent" in {
    forAll(addressGen) { address =>
      val expected = s"""
                        |{
                        |  "parent": "$address"
                        |}""".stripMargin
      check(ContractParent(Some(address)), expected)
      check(ContractParent(None), "{}")
    }
  }

  "SubContracts" in {
    forAll(Gen.nonEmptyListOf(addressGen)) { addresses =>
      val expected = s"""
                        |{
                        |  "subContracts": ${write(addresses)}
                        |}""".stripMargin
      check(SubContracts(addresses), expected)
      check(SubContracts(ArraySeq.empty), """{"subContracts":[]}""")
    }
  }

  "Groupless Address" in {
    val address     = "3cUqj91Y4SxeoV5szxWc6dekfDt6Pq1ZUC2kdeTW26rYXt3bY98YX"
    val addressLike = AddressLike.fromBase58(address).get

    addressLike.toBase58 is address

    (0 to groupConfig.groups - 1).foreach { i =>
      val grouped = AddressLike.fromBase58(address ++ s":$i").get

      grouped.getAddress().groupIndex.value is i

      grouped.toBase58 is address + s":$i"
    }
  }
}
