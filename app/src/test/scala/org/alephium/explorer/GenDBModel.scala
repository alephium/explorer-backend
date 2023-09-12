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

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.model.Script
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi.{blockEntryProtocolGen, valGen}
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, ChainIndex, GroupIndex, TransactionId}
import org.alephium.util.{AVector, TimeStamp}

/** Test-data generators for types in package [[org.alephium.explorer.persistence.model]] */
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
    } yield OutputEntity(
      blockHash = blockHash,
      txHash = txHash,
      timestamp = timestamp,
      outputType = outputType,
      hint = hint,
      key = key,
      amount = amount,
      address = address,
      tokens = tokens,
      mainChain = mainChain,
      lockTime = if (outputType == OutputEntity.Asset) lockTime else None,
      message = if (outputType == OutputEntity.Asset) message else None,
      outputOrder = outputOrder,
      txOrder = txOrder,
      spentFinalized = None,
      spentTimestamp = None,
      coinbase = coinbase
    )

  val finalizedOutputEntityGen: Gen[OutputEntity] =
    for {
      output         <- outputEntityGen
      spentFinalized <- Gen.option(transactionHashGen)
      spentTimestamp <- Gen.option(timestampGen)
    } yield output.copy(spentFinalized = spentFinalized, spentTimestamp = spentTimestamp)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def uoutputEntityGen(
      transactionHash: Gen[TransactionId] = transactionHashGen,
      address: Gen[Address] = addressGen
  ): Gen[UOutputEntity] =
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
    } yield UOutputEntity(
      txHash = txHash,
      hint = hint,
      key = key,
      amount = amount,
      address = address,
      tokens = tokens,
      lockTime = if (outputType == OutputEntity.Asset) lockTime else None,
      message = if (outputType == OutputEntity.Asset) message else None,
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
        blockHash = outputEntity.blockHash,
        txHash = outputEntity.txHash,
        timestamp = outputEntity.timestamp,
        hint = outputEntity.hint,
        outputRefKey = outputEntity.key,
        unlockScript = unlockScript,
        mainChain = outputEntity.mainChain,
        inputOrder = outputEntity.outputOrder,
        txOrder = txOrder,
        None,
        None,
        None,
        None
      )
    }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def uinputEntityGen(
      transactionHash: Gen[TransactionId] = transactionHashGen,
      address: Gen[Option[Address]] = Gen.option(addressGen)
  ): Gen[UInputEntity] =
    for {
      txHash       <- transactionHash
      hint         <- Gen.posNum[Int]
      outputRefKey <- hashGen
      unlockScript <- Gen.option(unlockScriptGen)
      address      <- address
      uinputOrder  <- arbitrary[Int]
    } yield UInputEntity(
      txHash,
      hint,
      outputRefKey,
      unlockScript,
      address,
      uinputOrder
    )

  /** Generates and [[org.alephium.explorer.persistence.model.InputEntity]] for the given
    * [[org.alephium.explorer.persistence.model.OutputEntity]] generator
    */
  def genInputOutput(
      outputGen: Gen[OutputEntity] = outputEntityGen
  ): Gen[(InputEntity, OutputEntity)] =
    for {
      output <- outputGen
      input  <- inputEntityGen(output)
    } yield (input, output)

  def genTransactionPerAddressEntity(
      addressGen: Gen[Address] = addressGen,
      timestampGen: Gen[TimeStamp] = timestampGen,
      mainChain: Gen[Boolean] = Arbitrary.arbitrary[Boolean]
  ): Gen[TransactionPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockHashGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- mainChain
      coinbase  <- Arbitrary.arbitrary[Boolean]
    } yield TransactionPerAddressEntity(
      address = address,
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      txOrder = txOrder,
      mainChain = mainChain,
      coinbase = coinbase
    )

  def transactionPerTokenEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen
  ): Gen[TransactionPerTokenEntity] =
    for {
      hash      <- transactionHashGen
      blockHash <- blockHash
      token     <- tokenIdGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
    } yield TransactionPerTokenEntity(
      hash = hash,
      blockHash = blockHash,
      token = token,
      timestamp = timestamp,
      txOrder = txOrder,
      mainChain = mainChain
    )

  def tokenTxPerAddressEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen
  ): Gen[TokenTxPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockHash
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
      token     <- tokenIdGen
    } yield TokenTxPerAddressEntity(
      address = address,
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      txOrder = txOrder,
      mainChain = mainChain,
      token = token
    )

  def eventEntityGen()(implicit groupSetting: GroupSetting): Gen[EventEntity] =
    for {
      blockHash       <- blockHashGen
      hash            <- transactionHashGen
      contractAddress <- addressGen
      inputAddress    <- Gen.option(addressGen)
      timestamp       <- timestampGen
      eventIndex      <- Gen.posNum[Int]
      fields          <- Gen.listOf(valGen())

    } yield EventEntity.from(
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
      addressGen: Gen[Address] = addressGen,
      transactionId: Gen[TransactionId] = transactionHashGen,
      blockHash: Gen[BlockHash] = blockHashGen,
      timestampGen: Gen[TimeStamp] = timestampGen,
      mainChain: Gen[Boolean] = Arbitrary.arbitrary[Boolean]
  ): Gen[TokenOutputEntity] =
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
    } yield TokenOutputEntity(
      blockHash,
      txHash,
      timestamp,
      outputType, // 0 Asset, 1 Contract
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

  def blockEntityGen(
      chainIndex: ChainIndex
  )(implicit groupSetting: GroupSetting): Gen[BlockEntity] =
    blockEntryProtocolGen.map { block =>
      BlockFlowClient.blockProtocolToEntity(
        block
          .copy(chainFrom = chainIndex.from.value, chainTo = chainIndex.to.value)
      )
    }

  def blockEntityWithParentGen(chainIndex: ChainIndex, parent: Option[BlockEntity])(implicit
      groupSetting: GroupSetting
  ): Gen[BlockEntity] =
    blockEntryProtocolGen.map { entry =>
      val deps = parent
        .map(p => entry.deps.replace(Generators.parentIndex(chainIndex.to), p.hash))
        .getOrElse(AVector.empty)
      val height    = parent.map(_.height.value + 1).getOrElse(0)
      val timestamp = parent.map(_.timestamp.plusHoursUnsafe(1)).getOrElse(ALPH.GenesisTimestamp)
      BlockFlowClient.blockProtocolToEntity(
        entry
          .copy(
            chainFrom = chainIndex.from.value,
            chainTo = chainIndex.to.value,
            timestamp = timestamp,
            height = height,
            deps = deps
          )
      )

    }

  /** Generates BlockEntity and it's dependant Entities that also maintain the block's mainChain
    * value
    */
  def blockAndItsMainChainEntitiesGen()(implicit
      groupSetting: GroupSetting
  ): Gen[(BlockEntity, TransactionPerTokenEntity, TokenTxPerAddressEntity, TokenOutputEntity)] =
    for {
      chainIndex        <- chainIndexGen
      entity            <- blockEntityWithParentGen(chainIndex, None)
      txnPerToken       <- transactionPerTokenEntityGen(blockHash = entity.hash)
      tokenTxPerAddress <- tokenTxPerAddressEntityGen(blockHash = entity.hash)
      tokenOutput       <- tokenOutputEntityGen(blockHash = entity.hash)
    } yield (entity, txnPerToken, tokenTxPerAddress, tokenOutput)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def transactionEntityGen(blockHash: Gen[BlockHash] = blockHashGen): Gen[TransactionEntity] =
    for {
      hash              <- transactionHashGen
      blockHash         <- blockHash
      timestamp         <- timestampGen
      chainFrom         <- groupIndexGen
      chainTo           <- groupIndexGen
      gasAmount         <- Gen.posNum[Int]
      gasPrice          <- u256Gen
      order             <- Gen.posNum[Int]
      mainChain         <- Arbitrary.arbitrary[Boolean]
      scriptExecutionOk <- Arbitrary.arbitrary[Boolean]
      version           <- Arbitrary.arbitrary[Byte]
      networkId         <- Arbitrary.arbitrary[Byte]
      scriptOpt         <- Gen.option(hashGen.map(hash => Script(hash.toHexString)))
      coinbase          <- Arbitrary.arbitrary[Boolean]
    } yield TransactionEntity(
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      chainFrom = chainFrom,
      chainTo = chainTo,
      gasAmount = gasAmount,
      gasPrice = gasPrice,
      order = order,
      mainChain = mainChain,
      scriptExecutionOk = scriptExecutionOk,
      inputSignatures = None,
      scriptSignatures = None,
      version = version,
      networkId = networkId,
      scriptOpt = scriptOpt,
      coinbase = coinbase
    )

  def mempoolTransactionEntityGen(): Gen[MempoolTransactionEntity] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      gasAmount <- Gen.posNum[Int]
      gasPrice  <- u256Gen
      timestamp <- timestampGen
    } yield MempoolTransactionEntity(
      hash = hash,
      chainFrom = chainFrom,
      chainTo = chainTo,
      gasAmount = gasAmount,
      gasPrice = gasPrice,
      lastSeen = timestamp
    )

  val blockHeaderGen: Gen[BlockHeader] =
    blockHeaderGenWithDefaults()

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def blockHeaderGenWithDefaults(
      chainFrom: Gen[GroupIndex] = groupIndexGen,
      chainTo: Gen[GroupIndex] = groupIndexGen,
      height: Gen[Height] = heightGen
  ): Gen[BlockHeader] =
    for {
      hash         <- blockHashGen
      timestamp    <- timestampGen
      chainFrom    <- chainFrom
      chainTo      <- chainTo
      height       <- height
      version      <- Gen.posNum[Byte]
      depStateHash <- hashGen
      txsHash      <- hashGen
      txsCount     <- Gen.posNum[Int]
      nonce        <- bytesGen
      target       <- bytesGen
      hashrate     <- arbitrary[Long].map(BigInteger.valueOf)
      mainChain    <- Arbitrary.arbitrary[Boolean]
      parent       <- Gen.option(blockHashGen)
    } yield BlockHeader(
      hash = hash,
      timestamp = timestamp,
      chainFrom = chainFrom,
      chainTo = chainTo,
      height = Height.unsafe(height.value),
      mainChain = mainChain,
      nonce = nonce,
      version = version,
      depStateHash = depStateHash,
      txsHash = txsHash,
      txsCount = txsCount,
      target = target,
      hashrate = hashrate,
      parent = parent
    )

  val blockHeaderTransactionEntityGen: Gen[(BlockHeader, List[TransactionEntity])] =
    for {
      blockHeader <- blockHeaderGen
      transaction <- Gen.listOf(transactionEntityGen(Gen.const(blockHeader.hash)))
    } yield (blockHeader, transaction)

  def blockHeaderWithHashrate(timestamp: TimeStamp, hashrate: Double): Gen[BlockHeader] = {
    for {
      hash         <- blockHashGen
      from         <- groupIndexGen
      to           <- groupIndexGen
      height       <- heightGen
      nonce        <- bytesGen
      version      <- Gen.posNum[Byte]
      depStateHash <- hashGen
      txsHash      <- hashGen
      txsCount     <- Gen.posNum[Int]
      target       <- bytesGen
      parent       <- Gen.option(blockHashGen)
    } yield {
      BlockHeader(
        hash,
        timestamp,
        from,
        to,
        height,
        true,
        nonce,
        version,
        depStateHash,
        txsHash,
        txsCount,
        target,
        BigDecimal(hashrate).toBigInt.bigInteger,
        parent
      )
    }
  }

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: BlockDepEntity, toUpdate: BlockDepEntity): BlockDepEntity =
    toUpdate.copy(
      hash = original.hash,
      dep = original.dep
    )

  val blockDepGen: Gen[BlockDepEntity] =
    for {
      hash  <- blockHashGen
      dep   <- blockHashGen
      order <- Gen.posNum[Int]
    } yield BlockDepEntity(
      hash = hash,
      dep = dep,
      order = order
    )

  /** Generates a tuple2 of [[BlockDepEntity]] where the second one has the same primary key as the
    * first one but with different values
    */
  val blockDepUpdatedGen: Gen[(BlockDepEntity, BlockDepEntity)] =
    for {
      dep1 <- blockDepGen
      dep2 <- blockDepGen
    } yield {
      (dep1, copyPrimaryKeys(dep1, dep2))
    }

  /** Table `uinputs` applies uniqueness on `(output_ref_key, tx_hash)`.
    *
    * Table `uoutputs` applies uniqueness on `(tx_hash, address, uoutput_order)`.
    *
    * These tables allow `txHashes` and `addresses` to have duplicate data and some of our SQL
    * queries run `DISTINCT` on these columns. This function generates test data for such queries.
    *
    * @return
    *   Generator that result in data that allows multiple [[UInputEntity]] with the same/duplicate
    *   `txHashes` and/or `addresses`.
    */
  def duplicateTxIdAndAddressGen(): Gen[(Gen[TransactionId], Gen[Address])] =
    for {
      txHashes <- Gen.nonEmptyListOf(transactionHashGen)
      address  <- Gen.nonEmptyListOf(addressGen)
    } yield (Gen.oneOf(txHashes), Gen.oneOf(address))

  def uinputEntityWithDuplicateTxIdsAndAddressesGen(): Gen[List[UInputEntity]] =
    for {
      (txHash, addressOpt) <- duplicateTxIdAndAddressGen()
      entities             <- Gen.listOf(uinputEntityGen(txHash, Gen.option(addressOpt)))
    } yield entities

  def uoutputEntityWithDuplicateTxIdsAndAddressesGen(): Gen[List[UOutputEntity]] =
    for {
      (txHash, addressOpt) <- duplicateTxIdAndAddressGen()
      entities             <- Gen.listOf(uoutputEntityGen(txHash, addressOpt))
    } yield {
      // uoutput allows duplicate `tx_hash` and `address` and there are queries
      // that search for such data but these duplicates must have unique `uoutputOrder`.
      // This sets all uoutputs to have have unique `uoutputOrder`.
      entities.zipWithIndex map { case (entity, index) =>
        entity.copy(uoutputOrder = index)
      }
    }

  def uInOutEntityWithDuplicateTxIdsAndAddressesGen()
      : Gen[(List[UInputEntity], List[UOutputEntity])] =
    for {
      (txHash, addressOpt) <- duplicateTxIdAndAddressGen()
      inputs               <- Gen.listOf(uinputEntityGen(txHash, Gen.option(addressOpt)))
      outputs              <- Gen.listOf(uoutputEntityGen(txHash, addressOpt))
    } yield (
      inputs,
      outputs.zipWithIndex map { case (entity, index) =>
        entity.copy(uoutputOrder = index)
      }
    )

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: InputEntity, toUpdate: InputEntity): InputEntity =
    toUpdate.copy(
      outputRefKey = original.outputRefKey,
      txHash = original.txHash,
      blockHash = original.blockHash
    )

  /** Generate two InputEntities where the second one is an update of the first */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def updatedInputEntityGen(
      outputEntityGen: Gen[OutputEntity] = outputEntityGen
  ): Gen[(InputEntity, InputEntity)] =
    for {
      input1 <- inputEntityGen(outputEntityGen)
      input2 <- inputEntityGen(outputEntityGen)
    } yield {
      (input1, copyPrimaryKeys(input1, input2))
    }

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: OutputEntity, toUpdate: OutputEntity): OutputEntity =
    toUpdate.copy(
      key = original.key,
      blockHash = original.blockHash
    )

  /** Generates a tuple2 of [[OutputEntity]] where the second one has the same primary key as the
    * first one but with different values
    */
  def updatedOutputEntityGen(): Gen[(OutputEntity, OutputEntity)] =
    for {
      output1 <- outputEntityGen
      output2 <- outputEntityGen
    } yield {
      (output1, copyPrimaryKeys(output1, output2))
    }

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: TransactionEntity, toUpdate: TransactionEntity): TransactionEntity =
    toUpdate.copy(hash = original.hash, blockHash = original.blockHash)

  /** Generates a [[BlockEntity]] with optional parent */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def updatedTransactionEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen
  ): Gen[(TransactionEntity, TransactionEntity)] =
    for {
      transaction1 <- transactionEntityGen(blockHash)
      transaction2 <- transactionEntityGen(blockHash)
    } yield {
      (transaction1, copyPrimaryKeys(transaction1, transaction2))
    }

  /** Update toUpdate's primary key to be the same as original */
  def copyPrimaryKeys(original: BlockHeader, toUpdate: BlockHeader): BlockHeader =
    toUpdate.copy(
      hash = original.hash,
      depStateHash = original.depStateHash
    )

  /** Generates a tuple2 of [[BlockHeader]] where the second one has the same primary key as the
    * first one but with different values
    */
  def updatedBlockHeaderGen(): Gen[(BlockHeader, BlockHeader)] =
    for {
      blockHeader1 <- blockHeaderGen
      blockHeader2 <- blockHeaderGen
    } yield {
      (blockHeader1, copyPrimaryKeys(blockHeader1, blockHeader2))
    }

  /** Generates a [[BlockEntity]] with optional parent
    * @param randomMainChainGen
    *   Some if randomness to mainChain should be applies else None. The generated boolean mainChain
    *   is set for both parent and child [[BlockEntity]].
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def genBlockEntityWithOptionalParent(
      groupIndexGen: Gen[GroupIndex] = Gen.const(GroupIndex.Zero),
      randomMainChainGen: Option[Gen[Boolean]] = None
  )(implicit groupSetting: GroupSetting): Gen[(BlockEntity, Option[BlockEntity])] =
    for {
      groupIndex <- groupIndexGen
      chainIndex = ChainIndex(groupIndex, groupIndex)
      parent <- Gen.option(blockEntityWithParentGen(chainIndex, None))
      child  <- blockEntityWithParentGen(chainIndex, parent)
    } yield {
      randomMainChainGen.flatMap(_.sample) match {
        case Some(randomMainChain) =>
          // set main_chain to generated sample
          val childWithRandomMainChain  = child.copy(mainChain = randomMainChain)
          val parentWithRandomMainChain = parent.map(_.copy(mainChain = randomMainChain))

          (childWithRandomMainChain, parentWithRandomMainChain)

        case None =>
          (child, parent)
      }
    }

}
