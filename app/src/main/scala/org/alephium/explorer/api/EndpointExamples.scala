// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import sttp.tapir.EndpointIO.Example

import org.alephium.api.EndpointsExamples
import org.alephium.api.model.{Address => ApiAddress, Amount, ValBool}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.queries.ExplainResult
import org.alephium.protocol.{ALPH, PublicKey}
import org.alephium.protocol.mining.HashRate
import org.alephium.protocol.model.{Address, BlockHash, ContractId, GroupIndex, TokenId}
import org.alephium.util.{Hex, U256}

/** Contains OpenAPI Examples.
  */
// scalastyle:off magic.number number.of.methods
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object EndpointExamples extends EndpointsExamples {

  private def alph(value: Int): Amount =
    Amount(ALPH.oneAlph.mulUnsafe(U256.unsafe(value)))

  private def blockHash: BlockHash =
    BlockHash
      .from(Hex.unsafe("bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5"))
      .get

  def version: Byte   = 1
  def networkId: Byte = 0

  private def outputRef: OutputRef =
    OutputRef(hint = 23412, key = hash)

  private def publicKey = "d1b70d2226308b46da297486adb6b4f1a8c1842cb159ac5ec04f384fe2d6f5da28"
  private def unlockScript: ByteString =
    Hex.unsafe(publicKey)

  private def address1Str: String = "1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n"
  private def address1: Address =
    Address.fromBase58(address1Str).toOption.get

  private def address2: Address =
    Address.fromBase58("22fnZLkZJUSyhXgboirmJktWkEBRk1pV8L6gfpc53hvVM").toOption.get

  private def grouplessAddress: ApiAddress = ApiAddress.fromBase58(address1Str).toOption.get

  private def contract =
    ContractId
      .from(Hex.from("ac92820d7b37ab2b14eca20839a9c1ad5379e671c687b37a416a2754ea9fc412").get)
      .get

  private def addressContract: Address.Contract = Address.contract(
    contract
  )

  private def addressAsset: Address.Asset =
    Address.asset(address1.toBase58).toOption.get

  private def groupIndex1: GroupIndex = new GroupIndex(1)
  private def groupIndex2: GroupIndex = new GroupIndex(2)

  private def token = TokenId.hash("token")

  private def tokens: ArraySeq[Token] =
    ArraySeq(
      Token(TokenId.hash("token1"), alph(42).value),
      Token(TokenId.hash("token2"), alph(1000).value)
    )

  private def input: Input =
    Input(
      outputRef = outputRef,
      unlockScript = Some(unlockScript),
      txHashRef = Some(txId),
      address = Some(address1),
      attoAlphAmount = Some(U256.Two),
      tokens = Some(tokens),
      contractInput = false
    )

  private def outputAsset: AssetOutput =
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

  private def outputContract: Output =
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
  private def blockEntryLite: BlockEntryLite =
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

  private def blockEntry: BlockEntry =
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

  private def transaction: Transaction =
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

  private def acceptedTransaction: AcceptedTransaction =
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

  private def pendingTransaction: PendingTransaction =
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

  private def mempoolTransaction: MempoolTransaction =
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

  private def addressInfo =
    AddressInfo(
      balance = U256.Ten,
      lockedBalance = U256.Two,
      txNumber = 1
    )

  private def addressBalance =
    AddressBalance(
      balance = U256.Ten,
      lockedBalance = U256.Two
    )

  private def addressTokenBalance =
    AddressTokenBalance(
      tokenId = token,
      balance = U256.Ten,
      lockedBalance = U256.Two
    )

  private def alphHolder =
    HolderInfo(
      address = address,
      balance = U256.Ten
    )

  private def contractInfo =
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

  private def contractParent =
    ContractParent(Some(address1))

  private def subContracts =
    SubContracts(ArraySeq(address1, address2))

  private def event =
    Event(
      blockHash,
      ts,
      txId,
      address1,
      Some(address1),
      0,
      ArraySeq(ValBool(true))
    )

  private def hashRate =
    Hashrate(
      timestamp = ts,
      hashrate = BigDecimal(HashRate.a128EhPerSecond.value),
      value = BigDecimal(HashRate.a128EhPerSecond.value)
    )

  private def timedCount =
    TimedCount(
      timestamp = ts,
      totalCountAllChains = 10000000
    )

  private def timedPrice =
    TimedPrices(
      timestamps = ArraySeq(ts, ts),
      prices = ArraySeq(0.123, 0.123)
    )

  private def perChainCount =
    PerChainCount(
      chainFrom = 1,
      chainTo = 2,
      count = 10000000
    )

  private def perChainTimedCount =
    PerChainTimedCount(
      timestamp = ts,
      totalCountPerChain = ArraySeq(perChainCount, perChainCount)
    )

  private def explorerInfo =
    ExplorerInfo(
      releaseVersion = "1.11.2+17-00593e8e-SNAPSHOT",
      commit = "00593e8e8c718d6bd27fe218e7aa438ef56611cc",
      migrationsVersion = 1,
      lastFinalizedInputTime = ts,
      lastHoldersUpdate = ts
    )

  private def tokenSupply =
    TokenSupply(
      timestamp = ts,
      total = ALPH.MaxALPHValue.divUnsafe(U256.Billion),
      circulating = ALPH.MaxALPHValue.divUnsafe(U256.Billion).divUnsafe(U256.Two),
      reserved = U256.Ten,
      locked = U256.Ten,
      maximum = ALPH.MaxALPHValue
    )

  private def perChainHeight =
    PerChainHeight(
      chainFrom = 1,
      chainTo = 2,
      height = 1000,
      value = 1000
    )

  private def perChainDuration =
    PerChainDuration(
      chainFrom = 1,
      chainTo = 2,
      duration = 60,
      value = 60
    )

  private def explainResult =
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

  private def logbackValue =
    LogbackValue(
      name = "org.test",
      level = LogbackValue.Level.Debug
    )

  /** Examples
    */

  implicit def grouplessAddressArray2: List[Example[ArraySeq[ApiAddress]]] =
    simpleExample(ArraySeq(grouplessAddress))

  implicit def blockEntryLiteExample: List[Example[BlockEntryLite]] =
    simpleExample(blockEntryLite)

  implicit def blockEntryExample: List[Example[BlockEntry]] =
    simpleExample(blockEntry)

  implicit def transactionsExample: List[Example[ArraySeq[Transaction]]] =
    simpleExample(ArraySeq(transaction, transaction))

  implicit def listOfBlocksExample: List[Example[ListBlocks]] =
    simpleExample(ListBlocks(2, ArraySeq(blockEntryLite, blockEntryLite)))

  implicit def listTokensExample: List[Example[ArraySeq[TokenId]]] =
    simpleExample(ArraySeq(token))

  implicit def symbolExample: List[Example[ArraySeq[String]]] =
    simpleExample(ArraySeq("ALPH", "USDCeth", "WBTC", "WETH", "DAI", "AYIN"))

  implicit def listAddressesExample: List[Example[ArraySeq[Address]]] =
    simpleExample(ArraySeq(address1))

  implicit def transactionLikeExample: List[Example[TransactionLike]] =
    simpleExample(acceptedTransaction)

  implicit def transactionsLikeExample: List[Example[ArraySeq[TransactionLike]]] =
    simpleExample(ArraySeq[TransactionLike](acceptedTransaction, pendingTransaction))

  implicit def mempoolTransactionsExamle: List[Example[ArraySeq[MempoolTransaction]]] =
    simpleExample(ArraySeq(mempoolTransaction, mempoolTransaction))

  implicit def transactionExample: List[Example[Transaction]] =
    simpleExample(transaction)

  implicit def addressInfoExample: List[Example[AddressInfo]] =
    simpleExample(addressInfo)

  implicit def addressBalanceExample: List[Example[AddressBalance]] =
    simpleExample(addressBalance)

  implicit def addressTokenBalanceExample: List[Example[AddressTokenBalance]] =
    simpleExample(addressTokenBalance)

  implicit def addressTokensBalanceExample: List[Example[ArraySeq[AddressTokenBalance]]] =
    simpleExample(ArraySeq(addressTokenBalance))

  implicit def alphHoldersExample: List[Example[ArraySeq[HolderInfo]]] =
    simpleExample(ArraySeq(alphHolder))

  implicit def contractParentExample: List[Example[ContractParent]] =
    simpleExample(contractParent)

  implicit def contractInfoExample: List[Example[ContractLiveness]] =
    simpleExample(contractInfo)

  implicit def subContractsExample: List[Example[SubContracts]] =
    simpleExample(subContracts)

  implicit def eventsExample: List[Example[ArraySeq[Event]]] =
    simpleExample(ArraySeq(event, event))

  implicit def booleansExample: List[Example[ArraySeq[Boolean]]] =
    simpleExample(ArraySeq(true, false))

  implicit def hashrateExample: List[Example[ArraySeq[Hashrate]]] =
    simpleExample(ArraySeq(hashRate, hashRate))

  implicit def timedCountExample: List[Example[ArraySeq[TimedCount]]] =
    simpleExample(ArraySeq(timedCount, timedCount))

  implicit def timedPriceExample: List[Example[TimedPrices]] =
    simpleExample(timedPrice)

  implicit def perChainTimedCountExample: List[Example[ArraySeq[PerChainTimedCount]]] =
    simpleExample(ArraySeq(perChainTimedCount, perChainTimedCount))

  implicit def explorerInfoExample: List[Example[ExplorerInfo]] =
    simpleExample(explorerInfo)

  implicit def tokenSupplyExample: List[Example[ArraySeq[TokenSupply]]] =
    simpleExample(ArraySeq(tokenSupply, tokenSupply))

  implicit def perChainHeightExample: List[Example[ArraySeq[PerChainHeight]]] =
    simpleExample(ArraySeq(perChainHeight, perChainHeight))

  implicit def perChainDurationExample: List[Example[ArraySeq[PerChainDuration]]] =
    simpleExample(ArraySeq(perChainDuration, perChainDuration))

  implicit def explainResultExample: List[Example[ArraySeq[ExplainResult]]] =
    simpleExample(ArraySeq(explainResult))

  implicit def logbackValueExample: List[Example[ArraySeq[LogbackValue]]] =
    simpleExample(ArraySeq(logbackValue))

  implicit def stdInterfaceIdExample: List[Example[TokenStdInterfaceId]] =
    simpleExample(StdInterfaceId.FungibleToken.default)

  implicit def fungibleTokenMetadataExample: List[Example[FungibleTokenMetadata]] =
    simpleExample(FungibleTokenMetadata(token, "TK", "Token", U256.One))

  implicit def fungibleTokensMetadataExample: List[Example[ArraySeq[FungibleTokenMetadata]]] =
    simpleExample(ArraySeq(FungibleTokenMetadata(token, "TK", "Token", U256.One)))

  implicit def nftsMetadataExample: List[Example[ArraySeq[NFTMetadata]]] =
    simpleExample(ArraySeq(NFTMetadata(token, "token://uri", contract, U256.One)))

  implicit def nftCollectionsMetadataExample: List[Example[ArraySeq[NFTCollectionMetadata]]] =
    simpleExample(ArraySeq(NFTCollectionMetadata(addressContract, "collection://uri")))

  implicit def tokenInfosExample: List[Example[ArraySeq[TokenInfo]]] =
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

  implicit def transactionInfoExample: List[Example[TransactionInfo]] =
    simpleExample(
      TransactionInfo(
        hash = txId,
        blockHash = blockHash,
        timestamp = ts,
        coinbase = false
      )
    )

  implicit def amountHistory: List[Example[AmountHistory]] =
    simpleExample(AmountHistory(ArraySeq(TimedAmount(ts, U256.One.v))))

  implicit def nftMetadataExample: List[Example[NFTMetadata]] =
    simpleExample(NFTMetadata(token, "token://uri", contract, U256.One))

  implicit def exchangeRatesExample: List[Example[ArraySeq[ExchangeRate]]] =
    simpleExample(ArraySeq(ExchangeRate("chf", "Swiss Franc", "Fr.", 0.01)))

  implicit def priceChartExample: List[Example[ArraySeq[(Long, Double)]]] =
    simpleExample(ArraySeq((1234545L, 0.01)))

  implicit def priceExample: List[Example[ArraySeq[Option[Double]]]] =
    simpleExample(ArraySeq(Option(0.01)))

  implicit def publicKeyExample: List[Example[PublicKey]] =
    simpleExample(PublicKey.unsafe(Hex.unsafe(publicKey)))
}
