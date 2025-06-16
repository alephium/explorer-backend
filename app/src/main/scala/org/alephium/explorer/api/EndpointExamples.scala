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
import org.alephium.api.model.{Amount, ValBool}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.queries.ExplainResult
import org.alephium.protocol.{ALPH, PublicKey}
import org.alephium.protocol.mining.HashRate
import org.alephium.protocol.model.{Address, BlockHash, ContractId, GroupIndex, TokenId}
import org.alephium.util.{Hex, U256}

/** Contains OpenAPI Examples.
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

  val version: Byte   = 1
  val networkId: Byte = 0

  private val outputRef: OutputRef =
    OutputRef(hint = 23412, key = hash)

  private val publicKey = "d1b70d2226308b46da297486adb6b4f1a8c1842cb159ac5ec04f384fe2d6f5da28"
  private val unlockScript: ByteString =
    Hex.unsafe(publicKey)

  private val address1: Address =
    Address.fromBase58("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n").get

  private val address2: Address =
    Address.fromBase58("22fnZLkZJUSyhXgboirmJktWkEBRk1pV8L6gfpc53hvVM").get

  private val contract =
    ContractId
      .from(Hex.from("ac92820d7b37ab2b14eca20839a9c1ad5379e671c687b37a416a2754ea9fc412").get)
      .get

  private val addressContract: Address.Contract = Address.contract(
    contract
  )

  private val addressAsset: Address.Asset = Address.asset(address1.toBase58).get

  private val groupIndex1: GroupIndex = new GroupIndex(1)
  private val groupIndex2: GroupIndex = new GroupIndex(2)

  private val token = TokenId.hash("token")

  private val tokens: ArraySeq[Token] =
    ArraySeq(
      Token(TokenId.hash("token1"), alph(42).value),
      Token(TokenId.hash("token2"), alph(1000).value)
    )

  private val input: Input =
    Input(
      outputRef = outputRef,
      unlockScript = Some(unlockScript),
      txHashRef = Some(txId),
      address = Some(address1),
      attoAlphAmount = Some(U256.Two),
      tokens = Some(tokens),
      contractInput = false
    )

  private val outputAsset: AssetOutput =
    AssetOutput(
      hint = 1,
      key = hash,
      attoAlphAmount = U256.Two,
      address = address1,
      tokens = Some(tokens),
      lockTime = Some(ts),
      message = Some(hash.bytes),
      fixedOutput = true
    )

  private val outputContract: Output =
    ContractOutput(
      hint = 1,
      key = hash,
      attoAlphAmount = U256.Two,
      address = address1,
      tokens = Some(tokens),
      fixedOutput = false
    )

  /** Main API objects
    */
  private val blockEntryLite: BlockEntryLite =
    BlockEntryLite(
      hash = blockHash,
      timestamp = ts,
      chainFrom = groupIndex1,
      chainTo = groupIndex2,
      height = Height.unsafe(42),
      txNumber = 1,
      mainChain = true,
      hashRate = HashRate.a128EhPerSecond.value
    )

  private val blockEntry: BlockEntry =
    BlockEntry(
      hash = blockHash,
      timestamp = ts,
      chainFrom = groupIndex1,
      chainTo = groupIndex2,
      height = Height.unsafe(42),
      deps = ArraySeq(blockHash),
      nonce = hash.bytes,
      version = 1,
      depStateHash = hash,
      txsHash = hash,
      txNumber = 1,
      target = hash.bytes,
      hashRate = HashRate.a128EhPerSecond.value,
      parent = Some(blockHash),
      mainChain = true,
      ghostUncles = ArraySeq(GhostUncle(blockHash, addressAsset))
    )

  private val transaction: Transaction =
    Transaction(
      hash = txId,
      blockHash = blockHash,
      timestamp = ts,
      inputs = ArraySeq(input),
      outputs = ArraySeq(outputAsset, outputContract),
      version = version,
      networkId = networkId,
      scriptOpt = None,
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      scriptExecutionOk = true,
      inputSignatures = ArraySeq(hash.bytes),
      scriptSignatures = ArraySeq(hash.bytes),
      coinbase = false
    )

  private val acceptedTransaction: AcceptedTransaction =
    AcceptedTransaction(
      hash = txId,
      blockHash = blockHash,
      timestamp = ts,
      inputs = ArraySeq(input),
      outputs = ArraySeq(outputAsset, outputContract),
      version = version,
      networkId = networkId,
      scriptOpt = None,
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      scriptExecutionOk = true,
      inputSignatures = ArraySeq(hash.bytes),
      scriptSignatures = ArraySeq(hash.bytes),
      coinbase = false
    )

  private val pendingTransaction: PendingTransaction =
    PendingTransaction(
      hash = txId,
      chainFrom = groupIndex1,
      chainTo = groupIndex2,
      inputs = ArraySeq(input),
      outputs = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      lastSeen = ts
    )

  private val mempoolTransaction: MempoolTransaction =
    MempoolTransaction(
      hash = txId,
      chainFrom = groupIndex1,
      chainTo = groupIndex2,
      inputs = ArraySeq(input),
      outputs = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.minimalGas.value,
      gasPrice = org.alephium.protocol.model.nonCoinbaseMinGasPrice.value,
      lastSeen = ts
    )

  private val addressInfo =
    AddressInfo(
      balance = U256.Ten,
      lockedBalance = U256.Two,
      txNumber = 1
    )

  private val addressBalance =
    AddressBalance(
      balance = U256.Ten,
      lockedBalance = U256.Two
    )

  private val addressTokenBalance =
    AddressTokenBalance(
      tokenId = token,
      balance = U256.Ten,
      lockedBalance = U256.Two
    )

  private val alphHolder =
    HolderInfo(
      address = address,
      balance = U256.Ten
    )

  private val contractInfo =
    ContractLiveness(
      Some(addressContract),
      ContractLiveness.Location(
        blockHash,
        txId,
        ts
      ),
      None,
      None
    )

  private val contractParent =
    ContractParent(Some(address1))

  private val subContracts =
    SubContracts(ArraySeq(address1, address2))

  private val event =
    Event(
      blockHash,
      ts,
      txId,
      address1,
      Some(address1),
      0,
      ArraySeq(ValBool(true))
    )

  private val hashRate =
    Hashrate(
      timestamp = ts,
      hashrate = BigDecimal(HashRate.a128EhPerSecond.value),
      value = BigDecimal(HashRate.a128EhPerSecond.value)
    )

  private val timedCount =
    TimedCount(
      timestamp = ts,
      totalCountAllChains = 10000000
    )

  private val timedPrice =
    TimedPrices(
      timestamps = ArraySeq(ts, ts),
      prices = ArraySeq(0.123, 0.123)
    )

  private val perChainCount =
    PerChainCount(
      chainFrom = 1,
      chainTo = 2,
      count = 10000000
    )

  private val perChainTimedCount =
    PerChainTimedCount(
      timestamp = ts,
      totalCountPerChain = ArraySeq(perChainCount, perChainCount)
    )

  private val explorerInfo =
    ExplorerInfo(
      releaseVersion = "1.11.2+17-00593e8e-SNAPSHOT",
      commit = "00593e8e8c718d6bd27fe218e7aa438ef56611cc",
      migrationsVersion = 1,
      lastFinalizedInputTime = ts,
      lastHoldersUpdate = ts
    )

  private val tokenSupply =
    TokenSupply(
      timestamp = ts,
      total = ALPH.MaxALPHValue.divUnsafe(U256.Billion),
      circulating = ALPH.MaxALPHValue.divUnsafe(U256.Billion).divUnsafe(U256.Two),
      reserved = U256.Ten,
      locked = U256.Ten,
      maximum = ALPH.MaxALPHValue
    )

  private val perChainHeight =
    PerChainHeight(
      chainFrom = 1,
      chainTo = 2,
      height = 1000,
      value = 1000
    )

  private val perChainDuration =
    PerChainDuration(
      chainFrom = 1,
      chainTo = 2,
      duration = 60,
      value = 60
    )

  private val explainResult =
    ExplainResult(
      queryName = "queryName",
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
      name = "org.test",
      level = LogbackValue.Level.Debug
    )

  /** Examples
    */
  implicit val blockEntryLiteExample: List[Example[BlockEntryLite]] =
    simpleExample(blockEntryLite)

  implicit val blockEntryExample: List[Example[BlockEntry]] =
    simpleExample(blockEntry)

  implicit val transactionsExample: List[Example[ArraySeq[Transaction]]] =
    simpleExample(ArraySeq(transaction, transaction))

  implicit val listOfBlocksExample: List[Example[ListBlocks]] =
    simpleExample(ListBlocks(2, ArraySeq(blockEntryLite, blockEntryLite)))

  implicit val listTokensExample: List[Example[ArraySeq[TokenId]]] =
    simpleExample(ArraySeq(token))

  implicit val symbolExample: List[Example[ArraySeq[String]]] =
    simpleExample(ArraySeq("ALPH", "USDCeth", "WBTC", "WETH", "DAI", "AYIN"))

  implicit val listAddressesExample: List[Example[ArraySeq[Address]]] =
    simpleExample(ArraySeq(address1))

  implicit val transactionLikeExample: List[Example[TransactionLike]] =
    simpleExample(acceptedTransaction)

  implicit val transactionsLikeExample: List[Example[ArraySeq[TransactionLike]]] =
    simpleExample(ArraySeq[TransactionLike](acceptedTransaction, pendingTransaction))

  implicit val mempoolTransactionsExamle: List[Example[ArraySeq[MempoolTransaction]]] =
    simpleExample(ArraySeq(mempoolTransaction, mempoolTransaction))

  implicit val transactionExample: List[Example[Transaction]] =
    simpleExample(transaction)

  implicit val addressInfoExample: List[Example[AddressInfo]] =
    simpleExample(addressInfo)

  implicit val addressBalanceExample: List[Example[AddressBalance]] =
    simpleExample(addressBalance)

  implicit val addressTokenBalanceExample: List[Example[AddressTokenBalance]] =
    simpleExample(addressTokenBalance)

  implicit val addressTokensBalanceExample: List[Example[ArraySeq[AddressTokenBalance]]] =
    simpleExample(ArraySeq(addressTokenBalance))

  implicit val alphHoldersExample: List[Example[ArraySeq[HolderInfo]]] =
    simpleExample(ArraySeq(alphHolder))

  implicit val contractParentExample: List[Example[ContractParent]] =
    simpleExample(contractParent)

  implicit val contractInfoExample: List[Example[ContractLiveness]] =
    simpleExample(contractInfo)

  implicit val subContractsExample: List[Example[SubContracts]] =
    simpleExample(subContracts)

  implicit val eventsExample: List[Example[ArraySeq[Event]]] =
    simpleExample(ArraySeq(event, event))

  implicit val booleansExample: List[Example[ArraySeq[Boolean]]] =
    simpleExample(ArraySeq(true, false))

  implicit val hashrateExample: List[Example[ArraySeq[Hashrate]]] =
    simpleExample(ArraySeq(hashRate, hashRate))

  implicit val timedCountExample: List[Example[ArraySeq[TimedCount]]] =
    simpleExample(ArraySeq(timedCount, timedCount))

  implicit val timedPriceExample: List[Example[TimedPrices]] =
    simpleExample(timedPrice)

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

  implicit val stdInterfaceIdExample: List[Example[TokenStdInterfaceId]] =
    simpleExample(StdInterfaceId.FungibleToken.default)

  implicit val fungibleTokenMetadataExample: List[Example[FungibleTokenMetadata]] =
    simpleExample(FungibleTokenMetadata(token, "TK", "Token", U256.One))

  implicit val fungibleTokensMetadataExample: List[Example[ArraySeq[FungibleTokenMetadata]]] =
    simpleExample(ArraySeq(FungibleTokenMetadata(token, "TK", "Token", U256.One)))

  implicit val nftsMetadataExample: List[Example[ArraySeq[NFTMetadata]]] =
    simpleExample(ArraySeq(NFTMetadata(token, "token://uri", contract, U256.One)))

  implicit val nftCollectionsMetadataExample: List[Example[ArraySeq[NFTCollectionMetadata]]] =
    simpleExample(ArraySeq(NFTCollectionMetadata(addressContract, "collection://uri")))

  implicit val tokenInfosExample: List[Example[ArraySeq[TokenInfo]]] =
    simpleExample(
      ArraySeq(
        TokenInfo(
          token,
          Some(StdInterfaceId.FungibleToken.default),
          Some(StdInterfaceId.FungibleToken("000101").id),
          Some(StdInterfaceId.FungibleToken.default.category)
        )
      )
    )

  implicit val transactionInfoExample: List[Example[TransactionInfo]] =
    simpleExample(
      TransactionInfo(
        hash = txId,
        blockHash = blockHash,
        timestamp = ts,
        coinbase = false
      )
    )

  implicit val amountHistory: List[Example[AmountHistory]] =
    simpleExample(AmountHistory(ArraySeq(TimedAmount(ts, U256.One.v))))

  implicit val nftMetadataExample: List[Example[NFTMetadata]] =
    simpleExample(NFTMetadata(token, "token://uri", contract, U256.One))

  implicit val exchangeRatesExample: List[Example[ArraySeq[ExchangeRate]]] =
    simpleExample(ArraySeq(ExchangeRate("chf", "Swiss Franc", "Fr.", 0.01)))

  implicit val priceChartExample: List[Example[ArraySeq[(Long, Double)]]] =
    simpleExample(ArraySeq((1234545L, 0.01)))

  implicit val priceExample: List[Example[ArraySeq[Option[Double]]]] =
    simpleExample(ArraySeq(Option(0.01)))

  implicit val publicKeyExample: List[Example[PublicKey]] =
    simpleExample(PublicKey.unsafe(Hex.unsafe(publicKey)))
}
