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

import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{BlockHash, ContractId, TokenId, TransactionId, TxOutputRef}
import org.alephium.util.{Base58, Number, U256}

/** Generators for types supplied by `org.alephium.explorer.api.model` package */
object GenApiModel extends ImplicitConversions {

  val hashGen: Gen[Hash]                     = Gen.const(()).map(_ => Hash.generate)
  val blockHashGen: Gen[BlockHash]           = Gen.const(()).map(_ => BlockHash.generate)
  val blockEntryHashGen: Gen[BlockHash]      = blockHashGen
  val transactionHashGen: Gen[TransactionId] = hashGen.map(TransactionId.unsafe)
  val tokenIdGen: Gen[TokenId]               = hashGen.map(TokenId.unsafe)
  val outputRefKeyGen: Gen[TxOutputRef.Key]  = hashGen.map(new TxOutputRef.Key(_))
  val contractIdGen: Gen[ContractId]         = hashGen.map(ContractId.unsafe)
  val groupIndexGen: Gen[GroupIndex]         = Gen.posNum[Int].map(GroupIndex.unsafe(_))
  val heightGen: Gen[Height]                 = Gen.posNum[Int].map(Height.unsafe(_))
  val addressGen: Gen[Address]               = hashGen.map(hash => Address.unsafe(Base58.encode(hash.bytes)))
  val bytesGen: Gen[ByteString]              = hashGen.map(_.bytes)
  val hashrateGen: Gen[BigInteger]           = arbitrary[Long].map(BigInteger.valueOf)
  val amountGen: Gen[U256]                   = Gen.choose(1000L, Number.quadrillion).map(ALPH.nanoAlph)
  val exportTypeGen: Gen[ExportType] =
    Gen.oneOf(ArraySeq(ExportType.CSV: ExportType, ExportType.JSON: ExportType))

  val outputRefGen: Gen[OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- outputRefKeyGen.map(_.value)
  } yield OutputRef(hint, key)

  val unlockScriptGen: Gen[ByteString] = hashGen.map(_.bytes)

  val inputGen: Gen[Input] = for {
    outputRef    <- outputRefGen
    unlockScript <- Gen.option(unlockScriptGen)
    address      <- Gen.option(addressGen)
    amount       <- Gen.option(amountGen)
  } yield Input(outputRef, unlockScript, address, amount)

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
      hash      <- transactionHashGen
      blockHash <- blockEntryHashGen
      timestamp <- timestampGen
      gasAmount <- Gen.posNum[Int]
      gasPrice  <- u256Gen
      coinbase  <- arbitrary[Boolean]
    } yield
      Transaction(hash,
                  blockHash,
                  timestamp,
                  ArraySeq.empty,
                  ArraySeq.empty,
                  gasAmount,
                  gasPrice,
                  coinbase)

  val utransactionGen: Gen[UnconfirmedTransaction] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      inputs    <- Gen.listOfN(3, inputGen.map(_.copy(attoAlphAmount = None)))
      outputs   <- Gen.listOfN(3, assetOutputGen.map(_.copy(spent = None)))
      gasAmount <- Gen.posNum[Int]
      gasPrice  <- u256Gen
      lastSeen  <- timestampGen
    } yield
      UnconfirmedTransaction(hash,
                             chainFrom,
                             chainTo,
                             inputs,
                             outputs,
                             gasAmount,
                             gasPrice,
                             lastSeen)

  def chainIndexes(implicit groupSetting: GroupSetting): Seq[(GroupIndex, GroupIndex)] =
    for {
      i <- 0 to groupSetting.groupNum - 1
      j <- 0 to groupSetting.groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

  val blockEntryLiteGen: Gen[BlockEntryLite] =
    for {
      hash      <- blockEntryHashGen
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
      hash         <- blockEntryHashGen
      timestamp    <- timestampGen
      chainFrom    <- groupIndexGen
      chainTo      <- groupIndexGen
      height       <- heightGen
      deps         <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockEntryHashGen)
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
}
