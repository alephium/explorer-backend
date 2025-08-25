// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.util.Random

import akka.util.ByteString

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.AddressUtil
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

/** Data generators for JMH benchmarks.
  *
  * These generator should favour consistency over randomness so multiple benchmarks are comparable.
  */
//scalastyle:off magic.number
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object DataGenerator {

  val timestampMaxValue: TimeStamp = TimeStamp.unsafe(253370764800000L) // Jan 01 9999 00:00:00

  def genTransactions(
      count: Int = 10,
      blockHash: BlockHash = BlockHash.generate,
      blockTimestamp: TimeStamp = TimeStamp.now(),
      mainChain: Boolean = Random.nextBoolean()
  ): ArraySeq[TransactionEntity] =
    ArraySeq.fill(count) {
      TransactionEntity(
        hash = TransactionId.generate,
        blockHash = blockHash,
        timestamp = blockTimestamp,
        chainFrom = new GroupIndex(1),
        chainTo = new GroupIndex(3),
        version = 1,
        networkId = 1,
        scriptOpt = Some(Random.alphanumeric.take(10).mkString),
        gasAmount = Random.nextInt(1000),
        gasPrice = U256.unsafe(0),
        order = Random.nextInt(1000),
        mainChain = mainChain,
        conflicted = None,
        scriptExecutionOk = Random.nextBoolean(),
        inputSignatures = None,
        scriptSignatures = None,
        coinbase = false
      )
    }

  def genOutputEntity(
      transactions: ArraySeq[TransactionEntity]
  ): ArraySeq[OutputEntity] = {
    val coinbaseTxHash = transactions.last.hash
    transactions.zipWithIndex map { case (transaction, order) =>
      val address = addressGen.sample.get
      OutputEntity(
        blockHash = transaction.blockHash,
        txHash = transaction.hash,
        timestamp = transaction.timestamp,
        outputType = OutputEntity.OutputType.unsafe(Random.nextInt(2)),
        hint = Random.nextInt(1000),
        key = Hash.generate,
        amount = U256.unsafe(Random.nextInt(100)),
        address = address,
        grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
        tokens = None,
        mainChain = transaction.mainChain,
        conflicted = None,
        lockTime = Some(TimeStamp.now()),
        message = None,
        outputOrder = order,
        txOrder = order,
        coinbase = transaction.hash == coinbaseTxHash,
        spentFinalized = None,
        spentTimestamp = None,
        fixedOutput = Random.nextBoolean()
      )
    }
  }

  def genInputEntity(outputs: ArraySeq[OutputEntity]): ArraySeq[InputEntity] =
    outputs.zipWithIndex map { case (output, order) =>
      InputEntity(
        blockHash = output.blockHash,
        txHash = output.txHash,
        timestamp = output.timestamp,
        hint = Random.nextInt(1000),
        outputRefKey = output.key,
        unlockScript = Some(unlockScriptGen.sample.get),
        mainChain = output.mainChain,
        conflicted = None,
        inputOrder = order,
        txOrder = order,
        None,
        None,
        None,
        None,
        None,
        contractInput = Random.nextBoolean()
      )
    }

  def genBlockEntity(
      transactionsCount: Int = 10,
      blockHash: BlockHash = BlockHash.generate,
      timestamp: TimeStamp = TimeStamp.now(),
      mainChain: Boolean = Random.nextBoolean()
  ): BlockEntity = {
    val transactions =
      genTransactions(
        count = transactionsCount,
        blockHash = blockHash,
        blockTimestamp = timestamp,
        mainChain = mainChain
      )

    val outputs =
      genOutputEntity(transactions)

    val inputs =
      genInputEntity(outputs)

    BlockEntity(
      hash = blockHash,
      timestamp = timestamp,
      chainFrom = GroupIndex.Zero,
      chainTo = new GroupIndex(3),
      height = Height.genesis,
      deps = ArraySeq.fill(5)(BlockHash.generate),
      transactions = transactions,
      inputs = inputs,
      outputs = outputs,
      mainChain = mainChain,
      nonce = ByteString.fromString(Random.alphanumeric.take(10).mkString),
      version = 1,
      depStateHash = Hash.generate,
      txsHash = Hash.generate,
      target = ByteString.fromString(Random.alphanumeric.take(10).mkString),
      hashrate = BigInteger.valueOf(Random.nextLong(Long.MaxValue)),
      ghostUncles = ArraySeq.empty,
      conflictedTxs = None
    )
  }

  def genAddress(): ApiAddress =
    addressGen.sample.get

  def genTransactionPerAddressEntity(
      address: ApiAddress = genAddress()
  ): TransactionPerAddressEntity =
    TransactionPerAddressEntity(
      address = address.toProtocol(),
      grouplessAddress = AddressUtil.convertToGrouplessAddress(address.toProtocol()),
      hash = TransactionId.generate,
      blockHash = BlockHash.generate,
      timestamp = TimeStamp.now(),
      txOrder = Random.nextInt(100),
      mainChain = Random.nextBoolean(),
      conflicted = None,
      coinbase = false
    )
}
