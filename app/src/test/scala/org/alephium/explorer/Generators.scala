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

import akka.util.ByteString
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.{model => protocolApi}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.protocol.{model => protocol, ALPH}
import org.alephium.util.{AVector, Duration, TimeStamp, U256}

trait Generators {

  def groupSettingGen: Gen[GroupSetting] = Gen.choose(2, 4).map(groupNum => GroupSetting(groupNum))

  lazy val outputTypeGen: Gen[OutputEntity.OutputType] =
    Gen.oneOf(0, 1).map(OutputEntity.OutputType.unsafe)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def transactionEntityGen(
      blockHash: Gen[BlockEntry.Hash] = blockEntryHashGen): Gen[TransactionEntity] =
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
    } yield
      TransactionEntity(
        hash              = hash,
        blockHash         = blockHash,
        timestamp         = timestamp,
        chainFrom         = chainFrom,
        chainTo           = chainTo,
        gasAmount         = gasAmount,
        gasPrice          = gasPrice,
        order             = order,
        mainChain         = mainChain,
        scriptExecutionOk = scriptExecutionOk,
        inputSignatures   = None,
        scriptSignatures  = None
      )

  lazy val blockHeaderTransactionEntityGen: Gen[(BlockHeader, List[TransactionEntity])] =
    for {
      blockHeader <- blockHeaderGen
      transaction <- Gen.listOf(transactionEntityGen(Gen.const(blockHeader.hash)))
    } yield (blockHeader, transaction)

  lazy val blockHeaderGen: Gen[BlockHeader] =
    for {
      hash         <- blockEntryHashGen
      timestamp    <- timestampGen
      chainFrom    <- groupIndexGen
      chainTo      <- groupIndexGen
      height       <- heightGen
      version      <- Gen.posNum[Byte]
      depStateHash <- hashGen
      txsHash      <- hashGen
      txsCount     <- Gen.posNum[Int]
      nonce        <- bytesGen
      target       <- bytesGen
      hashrate     <- arbitrary[Long].map(BigInteger.valueOf)
      mainChain    <- Arbitrary.arbitrary[Boolean]
      parent       <- Gen.option(blockEntryHashGen)
    } yield
      BlockHeader(
        hash         = hash,
        timestamp    = timestamp,
        chainFrom    = GroupIndex.unsafe(chainFrom.value),
        chainTo      = GroupIndex.unsafe(chainTo.value),
        height       = Height.unsafe(height.value),
        mainChain    = mainChain,
        nonce        = nonce,
        version      = version,
        depStateHash = depStateHash,
        txsHash      = txsHash,
        txsCount     = txsCount,
        target       = target,
        hashrate     = hashrate,
        parent       = parent
      )

  private def parentIndex(chainTo: GroupIndex)(implicit groupSettings: GroupSetting) =
    groupSettings.groupNum - 1 + chainTo.value

  def addressAssetProtocolGen(implicit groupSettings: GroupSetting): Gen[protocol.Address.Asset] =
    for {
      group <- Gen.choose(0, groupSettings.groupNum - 1)
    } yield {
      val groupIndex     = protocol.GroupIndex.unsafe(group)(groupSettings.groupConfig)
      val (_, publicKey) = groupIndex.generateKey(groupSettings.groupConfig)
      protocol.Address.p2pkh(publicKey)
    }

  lazy val addressContractProtocolGen: Gen[protocol.Address.Contract] =
    for {
      contractId <- hashGen
    } yield {
      protocol.Address.contract(contractId)
    }

  lazy val outputRefProtocolGen: Gen[protocolApi.OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- hashGen
  } yield protocolApi.OutputRef(hint, key)

  lazy val inputProtocolGen: Gen[protocolApi.AssetInput] = for {
    outputRef    <- outputRefProtocolGen
    unlockScript <- hashGen.map(_.bytes)
  } yield protocolApi.AssetInput(outputRef, unlockScript)

  def fixedOutputAssetProtocolGen(
      implicit groupSettings: GroupSetting): Gen[protocolApi.FixedAssetOutput] =
    for {
      hint     <- Gen.posNum[Int]
      key      <- hashGen
      amount   <- amountGen
      lockTime <- timestampGen
      address  <- addressAssetProtocolGen
    } yield
      protocolApi.FixedAssetOutput(hint,
                                   key,
                                   protocolApi.Amount(amount),
                                   address,
                                   AVector.empty,
                                   lockTime,
                                   ByteString.empty)

  def outputAssetProtocolGen(implicit groupSettings: GroupSetting): Gen[protocolApi.AssetOutput] =
    fixedOutputAssetProtocolGen.map(_.upCast())

  def outputContractProtocolGen: Gen[protocolApi.ContractOutput] =
    for {
      hint    <- Gen.posNum[Int]
      key     <- hashGen
      amount  <- amountGen
      address <- addressContractProtocolGen
    } yield
      protocolApi.ContractOutput(hint, key, protocolApi.Amount(amount), address, AVector.empty)

  def outputProtocolGen(implicit groupSettings: GroupSetting): Gen[protocolApi.Output] =
    Gen.oneOf(outputAssetProtocolGen: Gen[protocolApi.Output],
              outputContractProtocolGen: Gen[protocolApi.Output])

  def scriptGen: Gen[protocolApi.Script] = Gen.hexStr.map(protocolApi.Script.apply)
  def unsignedTxGen(implicit groupSettings: GroupSetting): Gen[protocolApi.UnsignedTx] =
    for {
      hash       <- transactionHashGen
      version    <- Gen.posNum[Byte]
      networkId  <- Gen.posNum[Byte]
      scriptOpt  <- Gen.option(scriptGen)
      inputSize  <- Gen.choose(0, 10)
      inputs     <- Gen.listOfN(inputSize, inputProtocolGen)
      outputSize <- Gen.choose(2, 10)
      outputs    <- Gen.listOfN(outputSize, fixedOutputAssetProtocolGen)
      gasAmount  <- Gen.posNum[Int]
      gasPrice   <- Gen.posNum[Long].map(U256.unsafe)
    } yield
      protocolApi.UnsignedTx(hash.value,
                             version,
                             networkId,
                             scriptOpt,
                             gasAmount,
                             gasPrice,
                             AVector.from(inputs),
                             AVector.from(outputs))

  def transactionProtocolGen(implicit groupSettings: GroupSetting): Gen[protocolApi.Transaction] =
    for {
      unsigned             <- unsignedTxGen
      scriptExecutionOk    <- arbitrary[Boolean]
      contractInputsSize   <- Gen.choose(0, 5)
      contractInputs       <- Gen.listOfN(contractInputsSize, outputRefProtocolGen)
      generatedOutputsSize <- Gen.choose(0, 5)
      generatedOutputs     <- Gen.listOfN(generatedOutputsSize, outputProtocolGen)
      inputSignatures      <- Gen.listOfN(2, bytesGen)
      scriptSignatures     <- Gen.listOfN(2, bytesGen)
    } yield
      protocolApi.Transaction(unsigned,
                              scriptExecutionOk,
                              AVector.from(contractInputs),
                              AVector.from(generatedOutputs),
                              AVector.from(inputSignatures),
                              AVector.from(scriptSignatures))

  def transactionTemplateProtocolGen(
      implicit groupSettings: GroupSetting): Gen[protocolApi.TransactionTemplate] =
    for {
      unsigned         <- unsignedTxGen
      inputSignatures  <- Gen.listOfN(2, bytesGen)
      scriptSignatures <- Gen.listOfN(2, bytesGen)
    } yield
      protocolApi.TransactionTemplate(unsigned,
                                      AVector.from(inputSignatures),
                                      AVector.from(scriptSignatures))

  def blockEntryProtocolGen(implicit groupSettings: GroupSetting): Gen[protocolApi.BlockEntry] =
    for {
      hash            <- blockEntryHashGen
      timestamp       <- timestampGen
      chainFrom       <- groupIndexGen
      chainTo         <- groupIndexGen
      height          <- heightGen
      deps            <- Gen.listOfN(2 * groupSettings.groupNum - 1, blockEntryHashGen)
      transactionSize <- Gen.choose(1, 10)
      transactions    <- Gen.listOfN(transactionSize, transactionProtocolGen)
      nonce           <- bytesGen
      version         <- Gen.posNum[Byte]
      depStateHash    <- hashGen
      txsHash         <- hashGen
    } yield {
      //From `alephium` repo
      val numZerosAtLeastInHash = 37
      val target = protocol.Target.unsafe(
        BigInteger.ONE.shiftLeft(256 - numZerosAtLeastInHash).subtract(BigInteger.ONE))

      protocolApi.BlockEntry(
        hash.value,
        timestamp,
        chainFrom.value,
        chainTo.value,
        height.value,
        AVector.from(deps.map(_.value)),
        AVector.from(transactions),
        nonce,
        version,
        depStateHash,
        txsHash,
        target.bits
      )
    }

  def blockEntityGen(chainFrom: GroupIndex, chainTo: GroupIndex, parent: Option[BlockEntity])(
      implicit groupSettings: GroupSetting): Gen[BlockEntity] =
    blockEntryProtocolGen.map { entry =>
      val deps = parent
        .map(p => entry.deps.replace(parentIndex(chainTo), p.hash.value))
        .getOrElse(AVector.empty)
      val height    = Height.unsafe(parent.map(_.height.value + 1).getOrElse(0))
      val timestamp = parent.map(_.timestamp.plusHoursUnsafe(1)).getOrElse(ALPH.GenesisTimestamp)
      BlockFlowClient.blockProtocolToEntity(
        entry
          .copy(chainFrom = chainFrom.value,
                chainTo   = chainTo.value,
                timestamp = timestamp,
                height    = height.value,
                deps      = deps))

    }

  def chainGen(size: Int, startTimestamp: TimeStamp, chainFrom: GroupIndex, chainTo: GroupIndex)(
      implicit groupSettings: GroupSetting): Gen[Seq[protocolApi.BlockEntry]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((Seq.empty[protocolApi.BlockEntry], Height.genesis, startTimestamp)) {
          case ((acc, height, timestamp), block) =>
            val deps: AVector[BlockHash] =
              if (acc.isEmpty) {
                AVector.empty
              } else {
                block.deps.replace(parentIndex(chainTo), acc.last.hash)
              }
            val newBlock = block.copy(height = height.value,
                                      deps      = deps,
                                      timestamp = timestamp,
                                      chainFrom = chainFrom.value,
                                      chainTo   = chainTo.value)
            (acc :+ newBlock, Height.unsafe(height.value + 1), timestamp + Duration.unsafe(1))
        } match { case (block, _, _) => block }
    }

  def blockFlowGen(maxChainSize: Int, startTimestamp: TimeStamp)(
      implicit groupSettings: GroupSetting): Gen[Seq[Seq[protocolApi.BlockEntry]]] = {
    val indexes = chainIndexes
    Gen
      .listOfN(indexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(indexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to).sample.get
      })
  }

  def blockEntitiesToBlockEntries(blocks: Seq[Seq[BlockEntity]]): Seq[Seq[BlockEntry]] = {
    val outputs: Seq[OutputEntity] = blocks.flatMap(_.flatMap(_.outputs))

    blocks.map(_.map { block =>
      val transactions =
        block.transactions.map { tx =>
          Transaction(
            tx.hash,
            block.hash,
            block.timestamp,
            block.inputs
              .filter(_.txHash === tx.hash)
              .map(input => input.toApi(outputs.head)), //TODO Fix when we have a valid blockchain generator
            block.outputs.filter(_.txHash === tx.hash).map(_.toApi(None)),
            tx.gasAmount,
            tx.gasPrice
          )
        }
      BlockEntry(
        block.hash,
        block.timestamp,
        block.chainFrom,
        block.chainTo,
        block.height,
        block.deps,
        transactions,
        mainChain = true,
        BigInteger.ZERO
      )
    })
  }

  def blockHeaderWithHashrate(timestamp: TimeStamp, hashrate: Double): Gen[BlockHeader] = {
    for {
      hash         <- blockEntryHashGen
      from         <- groupIndexGen
      to           <- groupIndexGen
      height       <- heightGen
      nonce        <- bytesGen
      version      <- Gen.posNum[Byte]
      depStateHash <- hashGen
      txsHash      <- hashGen
      txsCount     <- Gen.posNum[Int]
      target       <- bytesGen
      parent       <- Gen.option(blockEntryHashGen)
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
      dep  = original.dep
    )

  lazy val blockDepGen: Gen[BlockDepEntity] =
    for {
      hash  <- blockEntryHashGen
      dep   <- blockEntryHashGen
      order <- Gen.posNum[Int]
    } yield
      BlockDepEntity(
        hash  = hash,
        dep   = dep,
        order = order
      )

  /** Generates a tuple2 of [[BlockDepEntity]] where the second one has
    * the same primary key as the first one but with different values */
  lazy val blockDepUpdatedGen: Gen[(BlockDepEntity, BlockDepEntity)] =
    for {
      dep1 <- blockDepGen
      dep2 <- blockDepGen
    } yield {
      (dep1, copyPrimaryKeys(dep1, dep2))
    }

  lazy val outputEntityGen: Gen[OutputEntity] =
    for {
      blockHash   <- blockEntryHashGen
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
        spentFinalized = None
      )

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def inputEntityGen(outputEntityGen: Gen[OutputEntity] = outputEntityGen): Gen[InputEntity] =
    for {
      outputEntity <- outputEntityGen
      unlockScript <- Gen.option(Gen.hexStr)
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
        None
      )
    }

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: InputEntity, toUpdate: InputEntity): InputEntity =
    toUpdate.copy(
      outputRefKey = original.outputRefKey,
      txHash       = original.txHash,
      blockHash    = original.blockHash
    )

  /** Generate two InputEntities where the second one is an update of the first */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def updatedInputEntityGen(
      outputEntityGen: Gen[OutputEntity] = outputEntityGen): Gen[(InputEntity, InputEntity)] =
    for {
      input1 <- inputEntityGen(outputEntityGen)
      input2 <- inputEntityGen(outputEntityGen)
    } yield {
      (input1, copyPrimaryKeys(input1, input2))
    }

  /** Update toUpdate's primary key to be the same as `original` */
  def copyPrimaryKeys(original: OutputEntity, toUpdate: OutputEntity): OutputEntity =
    toUpdate.copy(
      key       = original.key,
      blockHash = original.blockHash
    )

  /** Generates a tuple2 of [[OutputEntity]] where the second one has
    * the same primary key as the first one but with different values */
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
  def updatedTransactionEntityGen(blockHash: Gen[BlockEntry.Hash] = blockEntryHashGen)
    : Gen[(TransactionEntity, TransactionEntity)] =
    for {
      transaction1 <- transactionEntityGen(blockHash)
      transaction2 <- transactionEntityGen(blockHash)
    } yield {
      (transaction1, copyPrimaryKeys(transaction1, transaction2))
    }

  /** Update toUpdate's primary key to be the same as original */
  def copyPrimaryKeys(original: BlockHeader, toUpdate: BlockHeader): BlockHeader =
    toUpdate.copy(
      hash         = original.hash,
      depStateHash = original.depStateHash
    )

  /** Generates a tuple2 of [[BlockHeader]] where the second one has
    * the same primary key as the first one but with different values */
  def updatedBlockHeaderGen(): Gen[(BlockHeader, BlockHeader)] =
    for {
      blockHeader1 <- blockHeaderGen
      blockHeader2 <- blockHeaderGen
    } yield {
      (blockHeader1, copyPrimaryKeys(blockHeader1, blockHeader2))
    }

  /**
    * Generates a [[BlockEntity]] with optional paren
    * t
    * @param randomMainChainGen Some if randomness to mainChain should be applies else None.
    *                           The generated boolean mainChain is set for both parent and child [[BlockEntity]].
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def genBlockEntityWithOptionalParent(groupIndexGen: Gen[GroupIndex] =
                                         Gen.const(GroupIndex.unsafe(0)),
                                       randomMainChainGen: Option[Gen[Boolean]] = None)(
      implicit groupSettings: GroupSetting): Gen[(BlockEntity, Option[BlockEntity])] =
    for {
      groupIndex <- groupIndexGen
      parent     <- Gen.option(blockEntityGen(groupIndex, groupIndex, None))
      child      <- blockEntityGen(groupIndex, groupIndex, parent)
    } yield {
      randomMainChainGen.flatMap(_.sample) match {
        case Some(randomMainChain) =>
          //set main_chain to generated sample
          val childWithRandomMainChain  = child.copy(mainChain = randomMainChain)
          val parentWithRandomMainChain = parent.map(_.copy(mainChain = randomMainChain))

          (childWithRandomMainChain, parentWithRandomMainChain)

        case None =>
          (child, parent)
      }
    }
}

object Generators extends Generators
