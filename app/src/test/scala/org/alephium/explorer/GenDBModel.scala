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

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi.{blockEntryProtocolGen, valGen}
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model.GroupIndex
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{AVector, TimeStamp}

/** Test-data generators for types in package [[org.alephium.explorer.persistence.model]]  */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object GenDBModel {

  val outputTypeGen: Gen[OutputEntity.OutputType] =
    Gen.oneOf(0, 1).map(OutputEntity.OutputType.unsafe)

  val outputEntityGen: Gen[OutputEntity] =
    for {
      blockHash   <- blockHashGen
      txHash      <- transactionHashGen
      timestamp   <- timestampGen
      outputType  <- outputTypeGen
      hint        <- Gen.posNum[Int]
      key         <- hashGen
      amount      <- u256Gen
      address     <- addressGen
      tokens      <- Gen.option(Gen.listOf(tokenGen))
      lockTime    <- Gen.option(timestampGen)
      message     <- Gen.option(bytesGen)
      mainChain   <- arbitrary[Boolean]
      outputOrder <- arbitrary[Int]
      txOrder     <- arbitrary[Int]
      coinbase    <- arbitrary[Boolean]
    } yield
      OutputEntity(
        blockHash      = blockHash,
        txHash         = txHash,
        timestamp      = timestamp,
        outputType     = outputType,
        hint           = hint,
        key            = key,
        amount         = amount,
        address        = address,
        tokens         = tokens,
        mainChain      = mainChain,
        lockTime       = if (outputType == OutputEntity.Asset) lockTime else None,
        message        = if (outputType == OutputEntity.Asset) message else None,
        outputOrder    = outputOrder,
        txOrder        = txOrder,
        spentFinalized = None,
        spentTimestamp = None,
        coinbase       = coinbase
      )

  val finalizedOutputEntityGen: Gen[OutputEntity] =
    for {
      output         <- outputEntityGen
      spentFinalized <- Gen.option(transactionHashGen)
      spentTimestamp <- Gen.option(timestampGen)
    } yield output.copy(spentFinalized = spentFinalized, spentTimestamp = spentTimestamp)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def uoutputEntityGen(transactionHash: Gen[TransactionId] = transactionHashGen,
                       address: Gen[Address]               = addressGen): Gen[UOutputEntity] =
    for {
      txHash       <- transactionHash
      outputType   <- outputTypeGen
      hint         <- Gen.posNum[Int]
      key          <- hashGen
      amount       <- u256Gen
      address      <- address
      tokens       <- Gen.option(Gen.listOf(tokenGen))
      lockTime     <- Gen.option(timestampGen)
      message      <- Gen.option(bytesGen)
      uoutputOrder <- arbitrary[Int]
    } yield
      UOutputEntity(
        txHash       = txHash,
        hint         = hint,
        key          = key,
        amount       = amount,
        address      = address,
        tokens       = tokens,
        lockTime     = if (outputType == OutputEntity.Asset) lockTime else None,
        message      = if (outputType == OutputEntity.Asset) message else None,
        uoutputOrder = uoutputOrder
      )

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def inputEntityGen(outputEntityGen: Gen[OutputEntity] = outputEntityGen): Gen[InputEntity] =
    for {
      outputEntity <- outputEntityGen
      unlockScript <- Gen.option(unlockScriptGen)
      txOrder      <- arbitrary[Int]
    } yield {
      InputEntity(
        blockHash    = outputEntity.blockHash,
        txHash       = outputEntity.txHash,
        timestamp    = outputEntity.timestamp,
        hint         = outputEntity.hint,
        outputRefKey = outputEntity.key,
        unlockScript = unlockScript,
        mainChain    = outputEntity.mainChain,
        inputOrder   = outputEntity.outputOrder,
        txOrder      = txOrder,
        None,
        None,
        None,
        None
      )
    }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def uinputEntityGen(transactionHash: Gen[TransactionId] = transactionHashGen,
                      address: Gen[Option[Address]]       = Gen.option(addressGen)): Gen[UInputEntity] =
    for {
      txHash       <- transactionHash
      hint         <- Gen.posNum[Int]
      outputRefKey <- hashGen
      unlockScript <- Gen.option(unlockScriptGen)
      address      <- address
      uinputOrder  <- arbitrary[Int]
    } yield
      UInputEntity(
        txHash,
        hint,
        outputRefKey,
        unlockScript,
        address,
        uinputOrder
      )

  /** Generates and [[org.alephium.explorer.persistence.model.InputEntity]] for the given
    * [[org.alephium.explorer.persistence.model.OutputEntity]] generator */
  def genInputOutput(
      outputGen: Gen[OutputEntity] = outputEntityGen): Gen[(InputEntity, OutputEntity)] =
    for {
      output <- outputGen
      input  <- inputEntityGen(output)
    } yield (input, output)

  def genTransactionPerAddressEntity(
      addressGen: Gen[Address]     = addressGen,
      timestampGen: Gen[TimeStamp] = timestampGen,
      mainChain: Gen[Boolean]      = Arbitrary.arbitrary[Boolean]): Gen[TransactionPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockHashGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- mainChain
      coinbase  <- Arbitrary.arbitrary[Boolean]
    } yield
      TransactionPerAddressEntity(
        address   = address,
        hash      = hash,
        blockHash = blockHash,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain,
        coinbase  = coinbase
      )

  def transactionPerTokenEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen): Gen[TransactionPerTokenEntity] =
    for {
      hash      <- transactionHashGen
      blockHash <- blockHash
      token     <- tokenIdGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
    } yield
      TransactionPerTokenEntity(
        hash      = hash,
        blockHash = blockHash,
        token     = token,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain
      )

  def tokenTxPerAddressEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen): Gen[TokenTxPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockHash
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
      token     <- tokenIdGen
    } yield
      TokenTxPerAddressEntity(
        address   = address,
        hash      = hash,
        blockHash = blockHash,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain,
        token     = token
      )

  def eventEntityGen: Gen[EventEntity] =
    for {
      blockHash       <- blockHashGen
      hash            <- transactionHashGen
      contractAddress <- addressGen
      inputAddress    <- Gen.option(addressGen)
      timestamp       <- timestampGen
      eventIndex      <- Gen.posNum[Int]
      fields          <- Gen.listOf(valGen)

    } yield
      EventEntity.from(
        blockHash,
        hash,
        contractAddress,
        inputAddress,
        timestamp,
        eventIndex,
        fields,
        0
      )

  def tokenOutputEntityGen(
      addressGen: Gen[Address]          = addressGen,
      transactionId: Gen[TransactionId] = transactionHashGen,
      blockHash: Gen[BlockHash]         = blockHashGen,
      timestampGen: Gen[TimeStamp]      = timestampGen,
      mainChain: Gen[Boolean]           = Arbitrary.arbitrary[Boolean]): Gen[TokenOutputEntity] =
    for {
      blockHash      <- blockHash
      txHash         <- transactionId
      timestamp      <- timestampGen
      outputType     <- outputTypeGen
      hint           <- Gen.posNum[Int]
      key            <- hashGen
      token          <- tokenIdGen
      amount         <- amountGen
      address        <- addressGen
      mainChain      <- mainChain
      lockTime       <- Gen.option(timestampGen)
      message        <- Gen.option(bytesGen)
      outputOrder    <- Gen.posNum[Int]
      txOrder        <- Gen.posNum[Int]
      spentFinalized <- Gen.option(transactionId)
      spentTimestamp <- Gen.option(timestampGen)
    } yield
      TokenOutputEntity(
        blockHash,
        txHash,
        timestamp,
        outputType, //0 Asset, 1 Contract
        hint,
        key,
        token,
        amount,
        address,
        mainChain,
        lockTime,
        message,
        outputOrder,
        txOrder,
        spentFinalized,
        spentTimestamp
      )

  def blockEntityGen(chainFrom: GroupIndex, chainTo: GroupIndex)(
      implicit groupSetting: GroupSetting): Gen[BlockEntity] =
    blockEntryProtocolGen.map { block =>
      BlockFlowClient.blockProtocolToEntity(
        block
          .copy(chainFrom = chainFrom.value, chainTo = chainTo.value))
    }

  def blockEntityWithParentGen(
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      parent: Option[BlockEntity])(implicit groupSetting: GroupSetting): Gen[BlockEntity] =
    blockEntryProtocolGen.map { entry =>
      val deps = parent
        .map(p => entry.deps.replace(Generators.parentIndex(chainTo), p.hash))
        .getOrElse(AVector.empty)
      val height    = parent.map(_.height.value + 1).getOrElse(0)
      val timestamp = parent.map(_.timestamp.plusHoursUnsafe(1)).getOrElse(ALPH.GenesisTimestamp)
      BlockFlowClient.blockProtocolToEntity(
        entry
          .copy(chainFrom = chainFrom.value,
                chainTo   = chainTo.value,
                timestamp = timestamp,
                height    = height,
                deps      = deps))

    }

  /** Generates BlockEntity and it's dependant Entities that also maintain the block's mainChain value */
  def blockAndItsMainChainEntitiesGen()(implicit groupSetting: GroupSetting)
    : Gen[(BlockEntity, TransactionPerTokenEntity, TokenTxPerAddressEntity, TokenOutputEntity)] =
    for {
      chainFrom         <- groupIndexGen
      chainTo           <- groupIndexGen
      entity            <- blockEntityWithParentGen(chainFrom, chainTo, None)
      txnPerToken       <- transactionPerTokenEntityGen(blockHash = entity.hash)
      tokenTxPerAddress <- tokenTxPerAddressEntityGen(blockHash = entity.hash)
      tokenOutput       <- tokenOutputEntityGen(blockHash = entity.hash)
    } yield (entity, txnPerToken, tokenTxPerAddress, tokenOutput)
}
