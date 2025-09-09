// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only
package org.alephium.explorer

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.model.{Address => ApiAddress, Val, ValAddress, ValByteVec}
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.AddressUtil
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, ChainIndex, GroupIndex, TransactionId}
import org.alephium.util.{AVector, TimeStamp}

/** Test-data generators for types in package [[org.alephium.explorer.persistence.model]] */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
// scalastyle:off number.of.methods
object GenDBModel {

  val grouplessAddressP2PKGen: Gen[GrouplessAddress] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.p2pkLockupGen(groupIndex)
  } yield GrouplessAddress(ApiAddress.HalfDecodedP2PK(lockup.publicKey))

  val grouplessAddressP2HMPKGen: Gen[GrouplessAddress] = for {
    groupIndex <- groupIndexGen
    lockup     <- GenCoreProtocol.p2hmpkLockupGen(groupIndex)
  } yield GrouplessAddress(ApiAddress.HalfDecodedP2HMPK(lockup.p2hmpkHash))

  val grouplessAddressGen: Gen[GrouplessAddress] =
    Gen.oneOf(grouplessAddressP2PKGen, grouplessAddressP2HMPKGen)

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
      fixedOutput <- arbitrary[Boolean]
    } yield {
      OutputEntity(
        blockHash = blockHash,
        txHash = txHash,
        timestamp = timestamp,
        outputType = outputType,
        hint = hint,
        key = key,
        amount = amount,
        address = address,
        grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
        tokens = tokens,
        mainChain = mainChain,
        conflicted = None,
        lockTime = if (outputType == OutputEntity.Asset) lockTime else None,
        message = if (outputType == OutputEntity.Asset) message else None,
        outputOrder = outputOrder,
        txOrder = txOrder,
        spentFinalized = None,
        spentTimestamp = None,
        coinbase = coinbase,
        fixedOutput = fixedOutput
      )
    }

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
      grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
      tokens = tokens,
      lockTime = if (outputType == OutputEntity.Asset) lockTime else None,
      message = if (outputType == OutputEntity.Asset) message else None,
      uoutputOrder = uoutputOrder
    )

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def inputEntityGen(outputEntityGen: Gen[OutputEntity] = outputEntityGen): Gen[InputEntity] =
    for {
      outputEntity  <- outputEntityGen
      unlockScript  <- Gen.option(unlockScriptGen)
      txOrder       <- arbitrary[Int]
      contractInput <- arbitrary[Boolean]
    } yield {
      InputEntity(
        blockHash = outputEntity.blockHash,
        txHash = outputEntity.txHash,
        timestamp = outputEntity.timestamp,
        hint = outputEntity.hint,
        outputRefKey = outputEntity.key,
        unlockScript = unlockScript,
        mainChain = outputEntity.mainChain,
        conflicted = None,
        inputOrder = outputEntity.outputOrder,
        txOrder = txOrder,
        None,
        None,
        None,
        None,
        None,
        contractInput
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
      grouplessAddress = address.flatMap(AddressUtil.convertToGrouplessAddress),
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
      grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      txOrder = txOrder,
      mainChain = mainChain,
      conflicted = None,
      coinbase = coinbase
    )

  def transactionPerTokenEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen
  )(implicit groupSetting: GroupSetting): Gen[TransactionPerTokenEntity] =
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
      mainChain = mainChain,
      conflicted = None
    )

  def tokenTxPerAddressEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen
  )(implicit groupSetting: GroupSetting): Gen[TokenTxPerAddressEntity] =
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
      grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      txOrder = txOrder,
      mainChain = mainChain,
      conflicted = None,
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
  )(implicit groupSetting: GroupSetting): Gen[TokenOutputEntity] =
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
      AddressUtil.convertToGrouplessAddress(address),
      mainChain,
      conflicted = None,
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

  def blockEntityUpdatedGen(
      chainIndex: ChainIndex
  )(blockUpdate: BlockEntity => BlockEntity)(implicit
      groupSetting: GroupSetting
  ): Gen[BlockEntity] =
    blockEntityGen(chainIndex).map(blockUpdate)

  def blockEntityWithAddressGen(
      chainIndex: ChainIndex,
      address: Address
  )(implicit groupSetting: GroupSetting): Gen[BlockEntity] =
    blockEntityUpdatedGen(chainIndex) { block =>
      block.copy(outputs =
        block.outputs.map(
          _.copy(
            address = address,
            grouplessAddress = AddressUtil.convertToGrouplessAddress(address)
          )
        )
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
      hash                 <- transactionHashGen
      blockHash            <- blockHash
      timestamp            <- timestampGen
      chainFrom            <- groupIndexGen
      chainTo              <- groupIndexGen
      version              <- arbitrary[Byte]
      networkId            <- arbitrary[Byte]
      scriptOpt            <- Gen.option(hashGen.map(_.toHexString))
      gasAmount            <- gasAmountGen
      gasPrice             <- u256Gen
      order                <- Gen.posNum[Int]
      mainChain            <- Arbitrary.arbitrary[Boolean]
      signaturesSize       <- Gen.choose(0, 3)
      inputSignatures      <- Gen.option(Gen.listOfN(signaturesSize, hashGen.map(_.bytes)))
      scriptSignaturesSize <- Gen.choose(0, 3)
      scriptSignatures     <- Gen.option(Gen.listOfN(scriptSignaturesSize, hashGen.map(_.bytes)))
      scriptExecutionOk    <- Arbitrary.arbitrary[Boolean]
      coinbase             <- Arbitrary.arbitrary[Boolean]
    } yield TransactionEntity(
      hash = hash,
      blockHash = blockHash,
      timestamp = timestamp,
      chainFrom = chainFrom,
      chainTo = chainTo,
      version = version,
      networkId = networkId,
      scriptOpt = scriptOpt,
      gasAmount = gasAmount,
      gasPrice = gasPrice,
      order = order,
      mainChain = mainChain,
      conflicted = None,
      scriptExecutionOk = scriptExecutionOk,
      inputSignatures = inputSignatures.map(ArraySeq.from),
      scriptSignatures = scriptSignatures.map(ArraySeq.from),
      coinbase = coinbase
    )

  def mempoolTransactionEntityGen(): Gen[MempoolTransactionEntity] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      gasAmount <- gasAmountGen
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
      deps         <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockHashGen)
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
      parent = parent,
      deps = deps,
      ghostUncles = None,
      conflictedTxs = None
    )

  val blockHeaderTransactionEntityGen: Gen[(BlockHeader, List[TransactionEntity])] =
    for {
      blockHeader <- blockHeaderGen
      transaction <- Gen.listOf(transactionEntityGen(Gen.const(blockHeader.hash)))
    } yield (blockHeader, transaction)

  def blockHeaderWithHashrate(timestamp: TimeStamp, hashrate: Double)(implicit
      groupSetting: GroupSetting
  ): Gen[BlockHeader] = {
    for {
      hash            <- blockHashGen
      from            <- groupIndexGen
      to              <- groupIndexGen
      height          <- heightGen
      nonce           <- bytesGen
      version         <- Gen.posNum[Byte]
      depStateHash    <- hashGen
      txsHash         <- hashGen
      txsCount        <- Gen.posNum[Int]
      target          <- bytesGen
      parent          <- Gen.option(blockHashGen)
      deps            <- Gen.listOfN(2 * groupSetting.groupNum - 1, blockHashGen)
      ghostUnclesSize <- Gen.choose(0, 5)
      ghostUncles     <- Gen.option(Gen.listOfN(ghostUnclesSize, ghostUncleGen()))
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
        parent,
        deps,
        ghostUncles,
        conflictedTxs = None
      )
    }
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

  private val emptyByteVec: Val = ValByteVec(ByteString.empty)

  def createEventGen(groupIndex: GroupIndex, parentOpt: Option[Address] = None)(implicit
      groupSetting: GroupSetting
  ): Gen[EventEntity] =
    for {
      event       <- eventEntityGen()
      contract    <- addressContractProtocolGen
      interfaceId <- Gen.option(valByteVecGen)
    } yield {
      event.copy(
        contractAddress = ContractEntity.createContractEventAddress(groupIndex),
        fields = ArraySeq(
          ValAddress(contract),
          parentOpt.map(ValAddress.apply).getOrElse(emptyByteVec),
          interfaceId.getOrElse(emptyByteVec)
        )
      )
    }
  def createEventsGen(parentOpt: Option[Address] = None)(implicit
      groupSetting: GroupSetting
  ): Gen[(GroupIndex, Seq[EventEntity])] =
    for {
      groupIndex <- groupIndexGen
      events     <- Gen.nonEmptyListOf(createEventGen(groupIndex, parentOpt))
    } yield (groupIndex, events)

  def destroyEventGen(contract: Address)(implicit groupSetting: GroupSetting): Gen[EventEntity] =
    for {
      event <- eventEntityGen()
    } yield {
      val groupIndex = ChainIndex.from(event.blockHash)(groupSetting.groupConfig).from
      event.copy(
        contractAddress = ContractEntity.destroyContractEventAddress(groupIndex),
        fields = ArraySeq(
          ValAddress(contract)
        )
      )
    }
}
