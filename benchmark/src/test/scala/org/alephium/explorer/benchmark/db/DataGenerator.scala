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

package org.alephium.explorer.benchmark.db

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.util.Random

import akka.util.ByteString

import org.alephium.explorer.{GenApiModel, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.{Base58, TimeStamp, U256}

/**
  * Data generators for JMH benchmarks.
  *
  * These generator should favour consistency over
  * randomness so multiple benchmarks are comparable.
  */
//scalastyle:off magic.number
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object DataGenerator {

  val timestampMaxValue: TimeStamp = TimeStamp.unsafe(253370764800000L) //Jan 01 9999 00:00:00

  def genTransactions(count: Int                = 10,
                      blockHash: BlockHash      = BlockHash.generate,
                      blockTimestamp: TimeStamp = TimeStamp.now(),
                      mainChain: Boolean        = Random.nextBoolean()): ArraySeq[TransactionEntity] =
    ArraySeq.fill(count) {
      TransactionEntity(
        hash              = TransactionId.generate,
        blockHash         = blockHash,
        timestamp         = blockTimestamp,
        chainFrom         = GroupIndex.unsafe(1),
        chainTo           = GroupIndex.unsafe(3),
        gasAmount         = Random.nextInt(1000),
        gasPrice          = U256.unsafe(0),
        order             = Random.nextInt(1000),
        mainChain         = mainChain,
        scriptExecutionOk = Random.nextBoolean(),
        inputSignatures   = None,
        scriptSignatures  = None,
        coinbase          = false
      )
    }

  def genOutputEntity(transactions: ArraySeq[TransactionEntity]): ArraySeq[OutputEntity] = {
    val coinbaseTxHash = transactions.last.hash
    transactions.zipWithIndex map {
      case (transaction, order) =>
        OutputEntity(
          blockHash      = transaction.blockHash,
          txHash         = transaction.hash,
          timestamp      = transaction.timestamp,
          outputType     = OutputEntity.OutputType.unsafe(Random.nextInt(2)),
          hint           = Random.nextInt(1000),
          key            = Hash.generate,
          amount         = U256.unsafe(Random.nextInt(100)),
          address        = Address.unsafe(Random.alphanumeric.take(10).mkString),
          tokens         = None,
          mainChain      = transaction.mainChain,
          lockTime       = Some(TimeStamp.now()),
          message        = None,
          outputOrder    = order,
          txOrder        = order,
          coinbase       = transaction.hash == coinbaseTxHash,
          spentFinalized = None
        )
    }
  }

  def genInputEntity(outputs: ArraySeq[OutputEntity]): ArraySeq[InputEntity] =
    outputs.zipWithIndex map {
      case (output, order) =>
        InputEntity(
          blockHash    = output.blockHash,
          txHash       = output.txHash,
          timestamp    = output.timestamp,
          hint         = Random.nextInt(1000),
          outputRefKey = output.key,
          unlockScript = Some(GenApiModel.unlockScriptGen.sample.get),
          mainChain    = output.mainChain,
          inputOrder   = order,
          txOrder      = order,
          None,
          None,
          None,
          None
        )
    }

  def genBlockEntity(transactionsCount: Int = 10,
                     blockHash: BlockHash   = BlockHash.generate,
                     timestamp: TimeStamp   = TimeStamp.now(),
                     mainChain: Boolean     = Random.nextBoolean()): BlockEntity = {
    val transactions =
      genTransactions(
        count          = transactionsCount,
        blockHash      = blockHash,
        blockTimestamp = timestamp,
        mainChain      = mainChain
      )

    val outputs =
      genOutputEntity(transactions)

    val inputs =
      genInputEntity(outputs)

    val coinbaseTxId = transactions.last.hash

    BlockEntity(
      hash         = blockHash,
      timestamp    = timestamp,
      chainFrom    = GroupIndex.unsafe(0),
      chainTo      = GroupIndex.unsafe(3),
      height       = Height.genesis,
      deps         = ArraySeq.fill(5)(BlockHash.generate),
      transactions = transactions,
      inputs       = inputs,
      outputs      = outputs,
      mainChain    = mainChain,
      nonce        = ByteString.fromString(Random.alphanumeric.take(10).mkString),
      version      = 1,
      depStateHash = Hash.generate,
      txsHash      = Hash.generate,
      target       = ByteString.fromString(Random.alphanumeric.take(10).mkString),
      hashrate     = BigInteger.valueOf(Random.nextLong(Long.MaxValue)),
      coinbaseTxId = coinbaseTxId
    )
  }

  def genAddress(): Address =
    Address.unsafe(Base58.encode(Hash.generate.bytes))

  def genTransactionPerAddressEntity(address: Address = genAddress()): TransactionPerAddressEntity =
    TransactionPerAddressEntity(
      address   = address,
      hash      = TransactionId.generate,
      blockHash = BlockHash.generate,
      timestamp = TimeStamp.now(),
      txOrder   = Random.nextInt(100),
      mainChain = Random.nextBoolean(),
      coinbase  = false
    )
}
