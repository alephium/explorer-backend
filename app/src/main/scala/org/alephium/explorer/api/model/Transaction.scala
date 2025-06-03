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

import akka.util.ByteString
import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.api.UtilJson._
import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.Json._
import org.alephium.explorer.util.UtxoUtil
import org.alephium.json.Json._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}
import org.alephium.util.AVector

final case class Transaction(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    scriptExecutionOk: Boolean,
    inputSignatures: ArraySeq[ByteString],
    scriptSignatures: ArraySeq[ByteString],
    coinbase: Boolean
) {
  def toCsv(address: ApiAddress, metadata: Map[TokenId, FungibleTokenMetadata]): String = {
    val dateTime      = Instant.ofEpochMilli(timestamp.millis)
    val fromAddresses = UtxoUtil.fromAddresses(inputs)
    val toAddresses =
      UtxoUtil.toAddressesWithoutChangeAddresses(outputs, fromAddresses)
    val deltaAmount = UtxoUtil.deltaAmountForAddress(address, inputs, outputs)
    val amountHint = deltaAmount
      .map(delta =>
        new java.math.BigDecimal(delta).divide(new java.math.BigDecimal(ALPH.oneAlph.v))
      )
      .map(_.toString)
      .getOrElse("")

    val tokenEntries = UtxoUtil
      .deltaTokenAmountForAddress(address, inputs, outputs)
      .map { case (token, delta) =>
        val metadataOpt      = metadata.get(token)
        val asset            = metadataOpt.map(_.symbol).getOrElse(token.toHexString)
        val tokenFromAddress = UtxoUtil.fromTokenAddresses(token, inputs)
        val tokenToAddress =
          UtxoUtil.toTokenAddressesWithoutChangeAddresses(token, outputs, tokenFromAddress)
        val deltaHint = metadataOpt
          .map { metadata =>
            new java.math.BigDecimal(delta)
              .divide(new java.math.BigDecimal(10).pow(metadata.decimals.toIntUnsafe))
          }
          .map(_.toString)
          .getOrElse(delta.toString)
        // scalastyle:off
        Transaction.csvValue(dateTime, deltaHint, asset, hash, tokenFromAddress, tokenToAddress)
      // scalastyle:on
      }
      .mkString

    Transaction.csvValue(dateTime, amountHint, "ALPH", hash, fromAddresses, toAddresses) ++
      tokenEntries
  }

  def toProtocol(): org.alephium.api.model.Transaction = {
    val (inputContracts, inputAssets)    = inputs.partition(_.contractInput)
    val (fixedOutputs, generatedOutputs) = outputs.partition(_.fixedOutput)
    def unsigned: org.alephium.api.model.UnsignedTx = org.alephium.api.model.UnsignedTx(
      txId = hash,
      version = version,
      networkId = networkId,
      scriptOpt = scriptOpt.map(org.alephium.api.model.Script.apply),
      gasAmount = gasAmount,
      gasPrice = gasPrice,
      inputs = AVector.from(inputAssets.map(_.toProtocol())),
      fixedOutputs = AVector.from(fixedOutputs.flatMap(Output.toFixedAssetOutput))
    )
    org.alephium.api.model.Transaction(
      unsigned = unsigned,
      scriptExecutionOk = scriptExecutionOk,
      contractInputs = AVector.from(inputContracts.map(_.outputRef.toProtocol())),
      generatedOutputs = AVector.from(generatedOutputs.flatMap(Output.toProtocol)),
      inputSignatures = AVector.from(inputSignatures),
      scriptSignatures = AVector.from(scriptSignatures)
    )
  }
}

object Transaction {
  implicit val txRW: ReadWriter[Transaction] = macroRW

  val csvHeader: String =
    "Date,Amount,Currency,TxHash,FromAddresses,ToAddresses\n"

  def csvValue(
      dateTime: Instant,
      amount: String,
      currency: String,
      hash: TransactionId,
      fromAddresses: ArraySeq[Address],
      toAddresses: ArraySeq[Address]
  ): String = {
    s"$dateTime,$amount,$currency,${hash.toHexString},${fromAddresses.mkString("-")},${toAddresses.mkString("-")}\n"
  }

  implicit val schema: Schema[Transaction] = Schema.derived[Transaction]
}
