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

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import sttp.tapir.EndpointIO.Example

import org.alephium.api.EndpointsExamples
import org.alephium.api.model.Amount
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.queries.ExplainResult
import org.alephium.protocol.ALPH
import org.alephium.protocol.mining.HashRate
import org.alephium.protocol.model.{Address, BlockHash, TokenId}
import org.alephium.util.{Hex, U256}

/**
  * Contains OpenAPI Examples.
  */
// scalastyle:off magic.number
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object EndpointExamples extends EndpointsExamples {

  private def alph(value: Int): Amount =
    Amount(ALPH.oneAlph.mulUnsafe(U256.unsafe(value)))

  private val blockHash: BlockHash =
    BlockHash
      .from(Hex.unsafe("bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5"))
      .get

  private val outputRef: OutputRef =
    OutputRef(hint = 23412, key = hash)

  private val unlockScript: ByteString =
    Hex.unsafe("d1b70d2226308b46da297486adb6b4f1a8c1842cb159ac5ec04f384fe2d6f5da28")

  private val address1: Address =
    Address.fromBase58("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n").get

  private val address2: Address =
    Address.fromBase58("22fnZLkZJUSyhXgboirmJktWkEBRk1pV8L6gfpc53hvVM").get

  private val tokens: ArraySeq[Token] =
    ArraySeq(
      Token(TokenId.hash("token1"), alph(42).value),
      Token(TokenId.hash("token2"), alph(1000).value)
    )

  private val input: Input =
    Input(
      outputRef      = outputRef,
      unlockScript   = Some(unlockScript),
      txHashRef      = Some(txId),
      address        = Some(address1),
      attoAlphAmount = Some(U256.Two),
      tokens         = Some(tokens)
    )

  private val outputAsset: AssetOutput =
    AssetOutput(
      hint           = 1,
      key            = hash,
      attoAlphAmount = U256.Two,
      address        = address1,
      tokens         = Some(tokens),
      lockTime       = Some(ts),
      message        = Some(hash.bytes)
    )

  private val outputContract: Output =
    ContractOutput(
      hint           = 1,
      key            = hash,
      attoAlphAmount = U256.Two,
      address        = address1,
      tokens         = Some(tokens)
    )

  /**
    * Main API objects
    */
  private val blockEntryLite: BlockEntryLite =
    BlockEntryLite(
      hash      = blockHash,
      timestamp = ts,
      chainFrom = GroupIndex.unsafe(1),
      chainTo   = GroupIndex.unsafe(2),
      height    = Height.unsafe(42),
      txNumber  = 1,
      mainChain = true,
      hashRate  = HashRate.a128EhPerSecond.value
    )

  private val transaction: Transaction =
    Transaction(
      hash      = txId,
      blockHash = blockHash,
      timestamp = ts,
      inputs    = ArraySeq(input),
      outputs   = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice  = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      coinbase  = false
    )

  private val confirmedTransaction =
    ConfirmedTransaction.from(transaction)

  private val unconfirmedTransaction =
    UnconfirmedTransaction(
      hash      = txId,
      chainFrom = GroupIndex.unsafe(1),
      chainTo   = GroupIndex.unsafe(2),
      inputs    = ArraySeq(input),
      outputs   = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice  = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      lastSeen  = ts
    )

  private val addressInfo =
    AddressInfo(
      balance       = U256.Ten,
      lockedBalance = U256.Two,
      txNumber      = 1
    )

  private val addressBalance =
    AddressBalance(
      balance       = U256.Ten,
      lockedBalance = U256.Two
    )

  private val hashRate =
    Hashrate(
      timestamp = ts,
      hashrate  = BigDecimal(HashRate.a128EhPerSecond.value),
      value     = BigDecimal(HashRate.a128EhPerSecond.value)
    )

  private val timedCount =
    TimedCount(
      timestamp           = ts,
      totalCountAllChains = 10000000
    )

  private val perChainCount =
    PerChainCount(
      chainFrom = 1,
      chainTo   = 2,
      count     = 10000000
    )

  private val perChainTimedCount =
    PerChainTimedCount(
      timestamp          = ts,
      totalCountPerChain = ArraySeq(perChainCount, perChainCount)
    )

  private val explorerInfo =
    ExplorerInfo(
      releaseVersion = "1.11.2+17-00593e8e-SNAPSHOT",
      commit         = "00593e8e8c718d6bd27fe218e7aa438ef56611cc"
    )

  private val tokenSupply =
    TokenSupply(
      timestamp   = ts,
      total       = ALPH.MaxALPHValue.divUnsafe(U256.Billion),
      circulating = ALPH.MaxALPHValue.divUnsafe(U256.Billion).divUnsafe(U256.Two),
      reserved    = U256.Ten,
      locked      = U256.Ten,
      maximum     = ALPH.MaxALPHValue
    )

  private val perChainHeight =
    PerChainHeight(
      chainFrom = 1,
      chainTo   = 2,
      height    = 1000,
      value     = 1000
    )

  private val perChainDuration =
    PerChainDuration(
      chainFrom = 1,
      chainTo   = 2,
      duration  = 60,
      value     = 60
    )

  private val explainResult =
    ExplainResult(
      queryName  = "queryName",
      queryInput = "Pagination(0,20,false)",
      explain = Vector(
        "Seq Scan on table_name  (cost=0.00..850.88 rows=20088 width=198) (actual time=0.007..4.358 rows=20088 loops=1)",
        "  Filter: table_column",
        "Planning Time: 0.694 ms",
        "Execution Time: 5.432 ms"
      ),
      messages = Array(
        "Used table_column_idx = false",
        "Used table_pk         = true"
      ),
      passed = true
    )

  private val logbackValue =
    LogbackValue(
      name  = "org.test",
      level = LogbackValue.Level.Debug
    )

  /**
    * Examples
    */
  implicit val blockEntryLiteExample: List[Example[BlockEntryLite]] =
    simpleExample(blockEntryLite)

  implicit val transactionsExample: List[Example[ArraySeq[Transaction]]] =
    simpleExample(ArraySeq(transaction, transaction))

  implicit val listOfBlocksExample: List[Example[ListBlocks]] =
    simpleExample(ListBlocks(2, ArraySeq(blockEntryLite, blockEntryLite)))

  implicit val listTokensExample: List[Example[ArraySeq[TokenId]]] =
    simpleExample(tokens.map(_.id))

  implicit val listAddressesExample: List[Example[ArraySeq[Address]]] =
    simpleExample(ArraySeq(address1, address2))

  implicit val transactionLikeExample: List[Example[TransactionLike]] =
    simpleExample(confirmedTransaction)

  implicit val unconfirmedTransactionsLike: List[Example[ArraySeq[UnconfirmedTransaction]]] =
    simpleExample(ArraySeq(unconfirmedTransaction, unconfirmedTransaction))

  implicit val transactionExample: List[Example[Transaction]] =
    simpleExample(transaction)

  implicit val addressInfoExample: List[Example[AddressInfo]] =
    simpleExample(addressInfo)

  implicit val addressBalanceExample: List[Example[AddressBalance]] =
    simpleExample(addressBalance)

  implicit val booleansExample: List[Example[ArraySeq[Boolean]]] =
    simpleExample(ArraySeq(true, false))

  implicit val hashrateExample: List[Example[ArraySeq[Hashrate]]] =
    simpleExample(ArraySeq(hashRate, hashRate))

  implicit val timedCountExample: List[Example[ArraySeq[TimedCount]]] =
    simpleExample(ArraySeq(timedCount, timedCount))

  implicit val perChainTimedCountExample: List[Example[ArraySeq[PerChainTimedCount]]] =
    simpleExample(ArraySeq(perChainTimedCount, perChainTimedCount))

  implicit val explorerInfoExample: List[Example[ExplorerInfo]] =
    simpleExample(explorerInfo)

  implicit val tokenSupplyExample: List[Example[ArraySeq[TokenSupply]]] =
    simpleExample(ArraySeq(tokenSupply, tokenSupply))

  implicit val perChainHeightExample: List[Example[ArraySeq[PerChainHeight]]] =
    simpleExample(ArraySeq(perChainHeight, perChainHeight))

  implicit val perChainDurationExample: List[Example[ArraySeq[PerChainDuration]]] =
    simpleExample(ArraySeq(perChainDuration, perChainDuration))

  implicit val explainResultExample: List[Example[ArraySeq[ExplainResult]]] =
    simpleExample(ArraySeq(explainResult))

  implicit val logbackValueExample: List[Example[ArraySeq[LogbackValue]]] =
    simpleExample(ArraySeq(logbackValue))
}
