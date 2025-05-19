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

package org.alephium.explorer

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

import akka.util.ByteString
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{
  Address,
  AddressLike,
  ChainIndex,
  GroupIndex,
  TokenId,
  TxOutputRef
}

/** Generators for types supplied by `org.alephium.explorer.api.model` package */
object GenApiModel extends ImplicitConversions {

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
  implicit def addressToLike(address: Address): AddressLike = {
    AddressLike.fromBase58(address.toBase58).get
  }

  def tokenIdGen(implicit gs: GroupSetting): Gen[TokenId] = for {
    contractId <- contractIdGen(gs)
  } yield {
    TokenId.from(contractId)
  }
  val outputRefKeyGen: Gen[TxOutputRef.Key] = hashGen.map(new TxOutputRef.Key(_))
  val groupIndexGen: Gen[GroupIndex] =
    Gen.choose(0, groupSetting.groupNum - 1).map(new GroupIndex(_))
  val chainIndexGen: Gen[ChainIndex] =
    for {
      from <- groupIndexGen
      to   <- groupIndexGen
    } yield ChainIndex(from, to)

  val heightGen: Gen[Height]       = Gen.posNum[Int].map(Height.unsafe(_))
  val bytesGen: Gen[ByteString]    = hashGen.map(_.bytes)
  val hashrateGen: Gen[BigInteger] = arbitrary[Long].map(BigInteger.valueOf)
  val exportTypeGen: Gen[ExportType] =
    Gen.oneOf(ArraySeq(ExportType.CSV: ExportType, ExportType.JSON: ExportType))

  val intervalTypeGen: Gen[IntervalType] =
    Gen.oneOf(ArraySeq[IntervalType](IntervalType.Hourly, IntervalType.Daily))

  val addressLikeGen: Gen[AddressLike] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.lockupGen(groupIndex)
  } yield AddressLike.from(lockup)

  val addressGen: Gen[Address] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.lockupGen(groupIndex)
  } yield Address.from(lockup)

  val grouplessAddressGen: Gen[Address] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.p2pkLockupGen(groupIndex)
  } yield Address.from(lockup)

  val outputRefGen: Gen[OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- outputRefKeyGen.map(_.value)
  } yield OutputRef(hint, key)

  val unlockScriptGen: Gen[ByteString] = hashGen.map(_.bytes)

  val inputGen: Gen[Input] = for {
    outputRef     <- outputRefGen
    unlockScript  <- Gen.option(unlockScriptGen)
    txHashRef     <- Gen.option(transactionHashGen)
    address       <- Gen.option(addressGen)
    amount        <- Gen.option(amountGen)
    tokens        <- Gen.option(tokensGen)
    contractInput <- arbitrary[Boolean]
  } yield Input(outputRef, unlockScript, txHashRef, address, amount, tokens, contractInput)

  val tokenGen: Gen[Token] = for {
    id     <- tokenIdGen
    amount <- amountGen
  } yield Token(id, amount)

  val tokensGen: Gen[Seq[Token]] = Gen.listOf(tokenGen)

  val inputWithTokensGen: Gen[Input] = for {
    input  <- inputGen
    tokens <- Gen.listOfN(2, tokenGen)
  } yield input.copy(tokens = Some(tokens))

  val assetOutputGen: Gen[AssetOutput] =
    for {
      amount   <- amountGen
      address  <- addressGen
      lockTime <- Gen.option(timestampGen)
      tokens   <- Gen.option(tokensGen)
      spent    <- Gen.option(transactionHashGen)
      message  <- Gen.option(bytesGen)
      hint = 0
      key         <- outputRefKeyGen.map(_.value)
      fixedOutput <- arbitrary[Boolean]
    } yield AssetOutput(hint, key, amount, address, tokens, lockTime, message, spent, fixedOutput)

  val contractOutputGen: Gen[ContractOutput] =
    for {
      amount  <- amountGen
      address <- addressGen
      tokens  <- Gen.option(tokensGen)
      spent   <- Gen.option(transactionHashGen)
      hint = 0
      key <- outputRefKeyGen.map(_.value)
    } yield ContractOutput(hint, key, amount, address, tokens, spent, fixedOutput = false)

  val outputGen: Gen[Output] =
    Gen.oneOf(assetOutputGen: Gen[Output], contractOutputGen: Gen[Output])

  val transactionGen: Gen[Transaction] =
    for {
      hash              <- transactionHashGen
      blockHash         <- blockHashGen
      timestamp         <- timestampGen
      outputs           <- Gen.listOfN(5, outputGen)
      version           <- arbitrary[Byte]
      networkId         <- arbitrary[Byte]
      scriptOpt         <- Gen.option(hashGen.map(_.toHexString))
      gasAmount         <- gasAmountGen
      gasPrice          <- u256Gen
      scriptExecutionOk <- arbitrary[Boolean]
      inputSignatures   <- Gen.listOfN(2, bytesGen)
      scriptSignatures  <- Gen.listOfN(2, bytesGen)
      coinbase          <- arbitrary[Boolean]
    } yield Transaction(
      hash,
      blockHash,
      timestamp,
      ArraySeq.empty,
      ArraySeq.from(outputs),
      version,
      networkId,
      scriptOpt,
      gasAmount,
      gasPrice,
      scriptExecutionOk,
      inputSignatures,
      scriptSignatures,
      coinbase
    )

  val mempooltransactionGen: Gen[MempoolTransaction] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      inputs <- Gen.listOfN(
        3,
        inputGen.map(
          _.copy(attoAlphAmount = None, txHashRef = None, tokens = None, contractInput = false)
        )
      )
      outputs   <- Gen.listOfN(3, assetOutputGen.map(_.copy(spent = None, fixedOutput = true)))
      gasAmount <- gasAmountGen
      gasPrice  <- u256Gen
      lastSeen  <- timestampGen
    } yield MempoolTransaction(
      hash,
      chainFrom,
      chainTo,
      inputs,
      outputs,
      gasAmount,
      gasPrice,
      lastSeen
    )

  def chainIndexes(implicit groupSetting: GroupSetting): Seq[ChainIndex] =
    for {
      i <- 0 to groupSetting.groupNum - 1
      j <- 0 to groupSetting.groupNum - 1
    } yield ChainIndex(
      GroupIndex.unsafe(i)(groupSetting.groupConfig),
      GroupIndex.unsafe(j)(groupSetting.groupConfig)
    )

  val blockEntryLiteGen: Gen[BlockEntryLite] =
    for {
      hash      <- blockHashGen
      timestamp <- timestampGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      height    <- heightGen
      txNumber  <- Gen.posNum[Int]
      mainChain <- arbitrary[Boolean]
      hashrate  <- hashrateGen
    } yield {
      BlockEntryLite(
        hash,
        timestamp,
        chainFrom,
        chainTo,
        height,
        txNumber,
        mainChain,
        hashrate
      )
    }

  def blockEntryGen(implicit groupSetting: GroupSetting): Gen[BlockEntry] =
    for {
      hash            <- blockHashGen
      timestamp       <- timestampGen
      chainFrom       <- groupIndexGen
      chainTo         <- groupIndexGen
      height          <- heightGen
      deps            <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockHashGen)
      nonce           <- bytesGen
      depStateHash    <- hashGen
      txsHash         <- hashGen
      txsCount        <- Gen.posNum[Int]
      target          <- bytesGen
      version         <- arbitrary[Byte]
      mainChain       <- arbitrary[Boolean]
      hashrate        <- arbitrary[Long].map(BigInteger.valueOf)
      parent          <- Gen.option(blockHashGen)
      ghostUnclesSize <- Gen.choose(0, 5)
      ghostUncles     <- Gen.listOfN(ghostUnclesSize, ghostUncleGen())
    } yield {
      BlockEntry(
        hash,
        timestamp,
        chainFrom,
        chainTo,
        height,
        deps,
        nonce,
        version,
        depStateHash,
        txsHash,
        txsCount,
        target,
        hashrate,
        parent,
        mainChain,
        ghostUncles
      )
    }

  val tokenSupplyGen: Gen[TokenSupply] =
    for {
      timestamp   <- timestampGen
      total       <- u256Gen
      circulating <- u256Gen
      reserved    <- u256Gen
      locked      <- u256Gen
      maximum     <- u256Gen
    } yield {
      TokenSupply(
        timestamp,
        total,
        circulating,
        reserved,
        locked,
        maximum
      )
    }

  val addressBalanceGen: Gen[AddressBalance] =
    for {
      balance       <- u256Gen
      lockedBalance <- u256Gen
    } yield {
      AddressBalance(
        balance,
        lockedBalance
      )
    }

  val addressTokenBalanceGen: Gen[AddressTokenBalance] =
    for {
      addressBalance <- addressBalanceGen
      tokenId        <- tokenIdGen
    } yield {
      AddressTokenBalance(
        tokenId,
        addressBalance.balance,
        addressBalance.lockedBalance
      )
    }

  val fungibleTokenMetadataGen: Gen[FungibleTokenMetadata] =
    for {
      tokenId  <- tokenIdGen
      symbol   <- Gen.alphaStr
      name     <- Gen.alphaStr
      decimals <- u256Gen
    } yield {
      FungibleTokenMetadata(
        tokenId,
        symbol,
        name,
        decimals
      )
    }

  val nftMetadataGen: Gen[NFTMetadata] =
    for {
      tokenId      <- tokenIdGen
      tokenUri     <- Gen.alphaStr
      collectionId <- contractIdGen
      nftIndex     <- u256Gen
    } yield {
      NFTMetadata(
        tokenId,
        tokenUri,
        collectionId,
        nftIndex
      )
    }

  val addressInfoGen: Gen[AddressInfo] =
    for {
      balance       <- u256Gen
      lockedBalance <- u256Gen
      txNumber      <- Gen.posNum[Int]
    } yield {
      AddressInfo(
        balance,
        lockedBalance,
        txNumber
      )
    }

  val listBlocksGen: Gen[ListBlocks] =
    for {
      total  <- Gen.choose[Int](1, 10)
      blocks <- Gen.listOfN(total, blockEntryLiteGen)
    } yield {
      ListBlocks(
        total,
        blocks
      )
    }

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  )
  val stdInterfaceIdGen: Gen[StdInterfaceId] =
    Gen.oneOf(
      ArraySeq(
        StdInterfaceId.FungibleToken.default,
        StdInterfaceId.NFTCollection,
        StdInterfaceId.NFTCollectionWithRoyalty,
        StdInterfaceId.NFT.default,
        StdInterfaceId.Unknown("1234"),
        StdInterfaceId.NonStandard
      )
    )

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  )
  val tokenInterfaceIdGen: Gen[StdInterfaceId] =
    Gen.oneOf(
      ArraySeq(
        StdInterfaceId.FungibleToken.default,
        StdInterfaceId.NFT.default,
        StdInterfaceId.NonStandard
      )
    )

  def holderInfoGen: Gen[HolderInfo] =
    for {
      address <- addressGen
      balance <- amountGen
    } yield HolderInfo(address, balance)

  def ghostUncleGen()(implicit groupSetting: GroupSetting): Gen[GhostUncle] = for {
    blockHash <- blockHashGen
    miner     <- addressAssetProtocolGen()
  } yield GhostUncle(blockHash, miner)

  /** Generates [[Pagination]] instance for the generated data.
    *
    * @return
    *   Pagination instance with the Generated data used to generate the Pagination instance
    */
  def paginationDataGen[T](dataGen: Gen[List[T]]): Gen[(List[T], Pagination)] =
    for {
      data       <- dataGen
      pagination <- paginationGen(Gen.const(data.size))
    } yield (data, pagination)

  /** Generates a [[Pagination]] instance with page between `1` and `maxDataCountGen.sample`.
    *
    * [[Pagination.page]] will at least have a minimum value of `1`.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def paginationGen(maxDataCountGen: Gen[Int] = Gen.choose(0, 10)): Gen[Pagination] =
    for {
      maxDataCount <- maxDataCountGen
      page  <- Gen.choose(maxDataCount min 1, maxDataCount) // Requirement: Page should be >= 1
      limit <- Gen.choose(0, maxDataCount)
    } yield Pagination.unsafe(page, limit)

}
