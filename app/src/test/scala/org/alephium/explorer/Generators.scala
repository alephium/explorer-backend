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
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import org.alephium.api.{model => protocolApi}
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.{model => protocol, _}
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.serde._
import org.alephium.util.{AVector, Duration, TimeStamp}

// scalastyle:off number.of.methods file.size.limit
object Generators {

  def groupSettingGen: Gen[GroupSetting] = Gen.choose(2, 4).map(groupNum => GroupSetting(groupNum))

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
      coinbase          <- Arbitrary.arbitrary[Boolean]
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
        scriptSignatures  = None,
        coinbase          = coinbase
      )

  def mempoolTransactionEntityGen(): Gen[MempoolTransactionEntity] =
    for {
      hash      <- transactionHashGen
      chainFrom <- groupIndexGen
      chainTo   <- groupIndexGen
      gasAmount <- Gen.posNum[Int]
      gasPrice  <- u256Gen
      timestamp <- timestampGen
    } yield
      MempoolTransactionEntity(
        hash      = hash,
        chainFrom = chainFrom,
        chainTo   = chainTo,
        gasAmount = gasAmount,
        gasPrice  = gasPrice,
        lastSeen  = timestamp
      )

  val blockHeaderGen: Gen[BlockHeader] =
    blockHeaderGenWithDefaults()

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def blockHeaderGenWithDefaults(chainFrom: Gen[GroupIndex] = groupIndexGen,
                                 chainTo: Gen[GroupIndex]   = groupIndexGen,
                                 height: Gen[Height]        = heightGen): Gen[BlockHeader] =
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

  val blockHeaderTransactionEntityGen: Gen[(BlockHeader, List[TransactionEntity])] =
    for {
      blockHeader <- blockHeaderGen
      transaction <- Gen.listOf(transactionEntityGen(Gen.const(blockHeader.hash)))
    } yield (blockHeader, transaction)

  def parentIndex(chainTo: GroupIndex)(implicit groupSetting: GroupSetting) =
    groupSetting.groupNum - 1 + chainTo.value

  def addressAssetProtocolGen: Gen[protocol.Address.Asset] =
    for {
      group <- Gen.choose(0, groupSetting.groupNum - 1)
    } yield {
      val groupIndex     = protocol.GroupIndex.unsafe(group)(groupSetting.groupConfig)
      val (_, publicKey) = groupIndex.generateKey(groupSetting.groupConfig)
      protocol.Address.p2pkh(publicKey)
    }

  val addressContractProtocolGen: Gen[protocol.Address.Contract] =
    for {
      contractId <- contractIdGen
    } yield {
      protocol.Address.contract(contractId)
    }

  val outputRefProtocolGen: Gen[protocolApi.OutputRef] = for {
    hint <- arbitrary[Int]
    key  <- hashGen
  } yield protocolApi.OutputRef(hint, key)

  val keypairGen: Gen[(PrivateKey, PublicKey)] =
    Gen.const(()).map(_ => SignatureSchema.secureGeneratePriPub())

  val publicKeyGen: Gen[PublicKey] =
    keypairGen.map(_._2)

  val unlockScriptProtocolP2PKHGen: Gen[vm.UnlockScript.P2PKH] =
    publicKeyGen.map(vm.UnlockScript.p2pkh)

  val unlockScriptProtocolP2MPKHGen: Gen[vm.UnlockScript.P2MPKH] =
    for {
      n          <- Gen.choose(5, 8)
      m          <- Gen.choose(2, 4)
      publicKey0 <- publicKeyGen
      moreKeys   <- Gen.listOfN(n, publicKeyGen)
      indexedKey <- Gen.pick(m, (publicKey0 +: moreKeys).zipWithIndex).map(AVector.from)
    } yield {
      vm.UnlockScript.p2mpkh(indexedKey.sortBy(_._2))
    }

  val methodGen: Gen[vm.Method[vm.StatelessContext]] = {
    for {
      useContractAssets <- Arbitrary.arbitrary[Boolean]
    } yield {
      vm.Method(
        isPublic             = true,
        usePreapprovedAssets = false,
        useContractAssets,
        argsLength   = 0,
        localsLength = 0,
        returnLength = 0,
        instrs       = AVector.empty[vm.Instr[vm.StatelessContext]]
      )
    }
  }

  val unlockScriptProtocolP2SHGen: Gen[vm.UnlockScript.P2SH] = {
    for {
      methods <- Gen.listOfN(4, methodGen)
    } yield {
      val script = vm.StatelessScript.unsafe(AVector.from(methods))
      vm.UnlockScript.P2SH(script, AVector.empty)
    }
  }

  val unlockScriptProtocolGen: Gen[vm.UnlockScript] =
    Gen.oneOf(unlockScriptProtocolP2PKHGen: Gen[vm.UnlockScript],
              unlockScriptProtocolP2MPKHGen: Gen[vm.UnlockScript])

  val inputProtocolGen: Gen[protocolApi.AssetInput] = for {
    outputRef    <- outputRefProtocolGen
    unlockScript <- unlockScriptProtocolGen
  } yield protocolApi.AssetInput(outputRef, serialize(unlockScript))

  def fixedOutputAssetProtocolGen: Gen[protocolApi.FixedAssetOutput] =
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

  def outputAssetProtocolGen: Gen[protocolApi.AssetOutput] =
    fixedOutputAssetProtocolGen.map(_.upCast())

  def outputContractProtocolGen: Gen[protocolApi.ContractOutput] =
    for {
      hint    <- Gen.posNum[Int]
      key     <- hashGen
      amount  <- amountGen
      address <- addressContractProtocolGen
    } yield
      protocolApi.ContractOutput(hint, key, protocolApi.Amount(amount), address, AVector.empty)

  def outputProtocolGen: Gen[protocolApi.Output] =
    Gen.oneOf(outputAssetProtocolGen: Gen[protocolApi.Output],
              outputContractProtocolGen: Gen[protocolApi.Output])

  def scriptGen: Gen[protocolApi.Script] = Gen.hexStr.map(protocolApi.Script.apply)

  def chainGen(size: Int,
               startTimestamp: TimeStamp,
               chainFrom: GroupIndex,
               chainTo: GroupIndex): Gen[ArraySeq[protocolApi.BlockEntry]] =
    Gen.listOfN(size, blockEntryProtocolGen).map { blocks =>
      blocks
        .foldLeft((ArraySeq.empty[protocolApi.BlockEntry], Height.genesis, startTimestamp)) {
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

  def blockFlowGen(maxChainSize: Int,
                   startTimestamp: TimeStamp): Gen[ArraySeq[ArraySeq[protocolApi.BlockEntry]]] = {
    val indexes = chainIndexes
    Gen
      .listOfN(indexes.size, Gen.choose(1, maxChainSize))
      .map(_.zip(indexes).map {
        case (size, (from, to)) =>
          chainGen(size, startTimestamp, from, to).sample.get
      })
  }

  def blockEntitiesToBlockEntries(
      blocks: ArraySeq[ArraySeq[BlockEntity]]): ArraySeq[ArraySeq[BlockEntry]] = {
    val outputs: ArraySeq[OutputEntity] = blocks.flatMap(_.flatMap(_.outputs))

    blocks.map(_.map { block =>
      val coinbaseTxId = block.transactions.last.hash
      val transactions =
        block.transactions.map {
          tx =>
            Transaction(
              tx.hash,
              block.hash,
              block.timestamp,
              block.inputs
                .filter(_.txHash === tx.hash)
                .map(input                                       => inputEntityToApi(input, outputs.head)), //TODO Fix when we have a valid blockchain generator
              block.outputs.filter(_.txHash === tx.hash).map(out => outputEntityToApi(out, None)),
              tx.gasAmount,
              tx.gasPrice,
              tx.scriptExecutionOk,
              coinbase = coinbaseTxId == tx.hash
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
      dep  = original.dep
    )

  val blockDepGen: Gen[BlockDepEntity] =
    for {
      hash  <- blockHashGen
      dep   <- blockHashGen
      order <- Gen.posNum[Int]
    } yield
      BlockDepEntity(
        hash  = hash,
        dep   = dep,
        order = order
      )

  /** Generates a tuple2 of [[BlockDepEntity]] where the second one has
    * the same primary key as the first one but with different values */
  val blockDepUpdatedGen: Gen[(BlockDepEntity, BlockDepEntity)] =
    for {
      dep1 <- blockDepGen
      dep2 <- blockDepGen
    } yield {
      (dep1, copyPrimaryKeys(dep1, dep2))
    }

  /**
    * Table `uinputs` applies uniqueness on `(output_ref_key, tx_hash)`.
    *
    * Table `uoutputs` applies uniqueness on `(tx_hash, address, uoutput_order)`.
    *
    * These tables allow `txHashes` and `addresses` to have duplicate data
    * and some of our SQL queries run `DISTINCT` on these columns.
    * This function generates test data for such queries.
    *
    * @return Generator that result in data that allows multiple [[UInputEntity]]
    *         with the same/duplicate `txHashes` and/or `addresses`.
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
      //uoutput allows duplicate `tx_hash` and `address` and there are queries
      //that search for such data but these duplicates must have unique `uoutputOrder`.
      //This sets all uoutputs to have have unique `uoutputOrder`.
      entities.zipWithIndex map {
        case (entity, index) =>
          entity.copy(uoutputOrder = index)
      }
    }

  def uInOutEntityWithDuplicateTxIdsAndAddressesGen()
    : Gen[(List[UInputEntity], List[UOutputEntity])] =
    for {
      (txHash, addressOpt) <- duplicateTxIdAndAddressGen()
      inputs               <- Gen.listOf(uinputEntityGen(txHash, Gen.option(addressOpt)))
      outputs              <- Gen.listOf(uoutputEntityGen(txHash, addressOpt))
    } yield
      (inputs, outputs.zipWithIndex map {
        case (entity, index) =>
          entity.copy(uoutputOrder = index)
      })

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
  def updatedTransactionEntityGen(
      blockHash: Gen[BlockHash] = blockHashGen): Gen[(TransactionEntity, TransactionEntity)] =
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
    * Generates a [[BlockEntity]] with optional parent
    * @param randomMainChainGen Some if randomness to mainChain should be applies else None.
    *                           The generated boolean mainChain is set for both parent and child [[BlockEntity]].
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def genBlockEntityWithOptionalParent(
      groupIndexGen: Gen[GroupIndex]           = Gen.const(GroupIndex.unsafe(0)),
      randomMainChainGen: Option[Gen[Boolean]] = None): Gen[(BlockEntity, Option[BlockEntity])] =
    for {
      groupIndex <- groupIndexGen
      parent     <- Gen.option(blockEntityWithParentGen(groupIndex, groupIndex, None))
      child      <- blockEntityWithParentGen(groupIndex, groupIndex, parent)
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

  /**
    * Generates [[Pagination]] instance for the generated data.
    *
    * @return Pagination instance with the Generated data
    *         used to generate the Pagination instance
    */
  def paginationDataGen[T](dataGen: Gen[List[T]]): Gen[(List[T], Pagination)] =
    for {
      data       <- dataGen
      pagination <- paginationGen(Gen.const(data.size))
    } yield (data, pagination)

  /**
    * Generates a [[Pagination]] instance with page between `1` and `maxDataCountGen.sample`.
    *
    * [[Pagination.page]] will at least have a minimum value of `1`.
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def paginationGen(maxDataCountGen: Gen[Int] = Gen.choose(0, 10)): Gen[Pagination] =
    for {
      maxDataCount <- maxDataCountGen
      page         <- Gen.choose(maxDataCount min 1, maxDataCount) //Requirement: Page should be >= 1
      limit        <- Gen.choose(0, maxDataCount)
    } yield Pagination.unsafe(page, limit)

  def inputEntityToApi(input: InputEntity, outputRef: OutputEntity): Input =
    Input(
      OutputRef(input.hint, input.outputRefKey),
      input.unlockScript,
      Some(outputRef.txHash),
      Some(outputRef.address),
      Some(outputRef.amount),
      outputRef.tokens
    )

  def outputEntityToApi(o: OutputEntity, spent: Option[TransactionId]): Output = {
    o.outputType match {
      case OutputEntity.Asset =>
        AssetOutput(o.hint, o.key, o.amount, o.address, o.tokens, o.lockTime, o.message, spent)
      case OutputEntity.Contract =>
        ContractOutput(o.hint, o.key, o.amount, o.address, o.tokens, spent)
    }
  }
}
