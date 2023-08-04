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

import akka.util.ByteString
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, ChainIndex, GroupIndex, TokenId, TxOutputRef}

/** Generators for types supplied by `org.alephium.explorer.api.model` package */
object GenApiModel extends ImplicitConversions {

  val tokenIdGen: Gen[TokenId]              = hashGen.map(TokenId.unsafe)
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

  val addressGen: Gen[Address] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.lockupGen(groupIndex)
  } yield Address.from(lockup)

  val outputRefGen: Gen[OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- outputRefKeyGen.map(_.value)
  } yield OutputRef(hint, key)

  val unlockScriptGen: Gen[ByteString] = hashGen.map(_.bytes)

  val inputGen: Gen[Input] = for {
    outputRef    <- outputRefGen
    unlockScript <- Gen.option(unlockScriptGen)
    txHashRef    <- Gen.option(transactionHashGen)
    address      <- Gen.option(addressGen)
    amount       <- Gen.option(amountGen)
  } yield Input(outputRef, unlockScript, txHashRef, address, amount)

  val tokenGen: Gen[Token] = for {
    id     <- tokenIdGen
    amount <- amountGen
  } yield Token(id, amount)

  val tokensGen: Gen[Seq[Token]] = Gen.listOf(tokenGen)

  val assetOutputGen: Gen[AssetOutput] =
    for {
      amount   <- amountGen
      address  <- addressGen
      lockTime <- Gen.option(timestampGen)
      tokens   <- Gen.option(tokensGen)
      spent    <- Gen.option(transactionHashGen)
      message  <- Gen.option(bytesGen)
      hint = 0
      key <- outputRefKeyGen.map(_.value)
    } yield AssetOutput(hint, key, amount, address, tokens, lockTime, message, spent)

  val contractOutputGen: Gen[ContractOutput] =
    for {
      amount  <- amountGen
      address <- addressGen
      tokens  <- Gen.option(tokensGen)
      spent   <- Gen.option(transactionHashGen)
      hint = 0
      key <- outputRefKeyGen.map(_.value)
    } yield ContractOutput(hint, key, amount, address, tokens, spent)

  val outputGen: Gen[Output] =
    Gen.oneOf(assetOutputGen: Gen[Output], contractOutputGen: Gen[Output])

  val transactionGen: Gen[Transaction] =
    for {
      hash              <- transactionHashGen
      blockHash         <- blockHashGen
      timestamp         <- timestampGen
      outputs           <- Gen.listOfN(5, outputGen)
      gasAmount         <- Gen.posNum[Int]
      gasPrice          <- u256Gen
      scriptExecutionOk <- arbitrary[Boolean]
      coinbase          <- arbitrary[Boolean]
    } yield Transaction(
      hash,
      blockHash,
      timestamp,
      ArraySeq.empty,
      ArraySeq.from(outputs),
      gasAmount,
      gasPrice,
      scriptExecutionOk,
      coinbase
    )

  val mempooltransactionGen: Gen[MempoolTransaction] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      inputs    <- Gen.listOfN(3, inputGen.map(_.copy(attoAlphAmount = None, txHashRef = None)))
      outputs   <- Gen.listOfN(3, assetOutputGen.map(_.copy(spent = None)))
      gasAmount <- Gen.posNum[Int]
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
      hash         <- blockHashGen
      timestamp    <- timestampGen
      chainFrom    <- groupIndexGen
      chainTo      <- groupIndexGen
      height       <- heightGen
      deps         <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockHashGen)
      transactions <- Gen.listOfN(2, transactionGen)
      mainChain    <- arbitrary[Boolean]
      hashrate     <- arbitrary[Long].map(BigInteger.valueOf)
    } yield {
      BlockEntry(
        hash,
        timestamp,
        chainFrom,
        chainTo,
        height,
        deps,
        transactions,
        mainChain,
        hashrate
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
