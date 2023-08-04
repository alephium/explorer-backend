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

package org.alephium.explorer.service

import java.math.BigInteger
import java.net.InetAddress

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import sttp.model.Uri

import org.alephium.api
import org.alephium.api.{ApiModelCodec, Endpoints}
import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.api.model._
import org.alephium.explorer.error.ExplorerError
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.InputAddressUtil
import org.alephium.http.EndpointSender
import org.alephium.protocol
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.mining.HashRate
import org.alephium.protocol.model.{
  Address,
  BlockHash,
  ChainIndex,
  GroupIndex,
  Hint,
  Target,
  TransactionId
}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.{AVector, Duration, Service, TimeStamp, U256}

trait BlockFlowClient extends Service {
  def fetchBlock(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntity]

  def fetchBlockAndEvents(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntityWithEvents]

  def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo]

  def fetchHashesAtHeight(chainIndex: ChainIndex, height: Height): Future[HashesAtHeight]

  def fetchBlocks(
      fromTs: TimeStamp,
      toTs: TimeStamp,
      uri: Uri
  ): Future[ArraySeq[ArraySeq[BlockEntityWithEvents]]]

  def fetchBlocksAtHeight(chainIndex: ChainIndex, height: Height)(implicit
      executionContext: ExecutionContext
  ): Future[ArraySeq[BlockEntity]] =
    fetchHashesAtHeight(chainIndex, height).flatMap { hashesAtHeight =>
      Future
        .sequence(
          hashesAtHeight.headers
            .map(hash => fetchBlock(chainIndex.from, hash))
            .toArraySeq
        )
    }

  def fetchSelfClique(): Future[SelfClique]

  def fetchChainParams(): Future[ChainParams]

  def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]]

  def start(): Future[Unit]

  def close(): Future[Unit]
}

object BlockFlowClient extends StrictLogging {
  def apply(
      uri: Uri,
      groupNum: Int,
      maybeApiKey: Option[api.model.ApiKey],
      directCliqueAccess: Boolean
  )(implicit
      executionContext: ExecutionContext
  ): BlockFlowClient =
    new Impl(uri, groupNum, maybeApiKey, directCliqueAccess)

  private class Impl(
      uri: Uri,
      groupNum: Int,
      val maybeApiKey: Option[api.model.ApiKey],
      directCliqueAccess: Boolean
  )(implicit
      val executionContext: ExecutionContext
  ) extends BlockFlowClient
      with Endpoints
      with ApiModelCodec {

    private val endpointSender = new EndpointSender(maybeApiKey)

    override def startSelfOnce(): Future[Unit] = {
      endpointSender.start()
    }

    override def stopSelfOnce(): Future[Unit] = {
      close()
    }

    override def subServices: ArraySeq[Service] = ArraySeq.empty

    implicit val groupSetting: GroupSetting = GroupSetting(groupNum)

    implicit val groupConfig: GroupConfig = groupSetting.groupConfig

    private def _send[A, B](
        endpoint: BaseEndpoint[A, B],
        uri: Uri,
        a: A
    ): Future[B] = {
      endpointSender
        .send(endpoint, a, uri)
        .flatMap {
          case Right(res) => Future.successful(res)
          case Left(error) =>
            Future.failed(NodeApiError(error.detail))
        }
        .recoverWith { error =>
          Future.failed(UnreachableNode(error))
        }
    }

    // If directCliqueAccess = true, we need to first get all nodes of the clique
    // to make sure we call the node which conains block's data
    def fetchBlock(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntity] =
      if (directCliqueAccess) {
        fetchSelfCliqueAndChainParams().flatMap { case (selfClique, chainParams) =>
          selfCliqueIndex(selfClique, chainParams, fromGroup) match {
            case Left(error) => Future.failed(new Throwable(error))
            case Right((nodeAddress, restPort)) =>
              val uri = Uri(nodeAddress.getHostAddress, restPort)
              _send(getBlock, uri, hash).map(blockProtocolToEntity)
          }
        }
      } else {
        _send(getBlock, uri, hash).map(blockProtocolToEntity)
      }

    def fetchBlockAndEvents(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntityWithEvents] =
      fetchSelfCliqueAndChainParams().flatMap { case (selfClique, chainParams) =>
        selfCliqueIndex(selfClique, chainParams, fromGroup) match {
          case Left(error) => Future.failed(new Throwable(error))
          case Right((nodeAddress, restPort)) =>
            val uri = Uri(nodeAddress.getHostAddress, restPort)
            _send(getBlockAndEvents, uri, hash).map(blockAndEventsToEntities)
        }
      }
    def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo] = {
      _send(getChainInfo, uri, chainIndex)
    }

    def fetchHashesAtHeight(chainIndex: ChainIndex, height: Height): Future[HashesAtHeight] =
      _send(getHashesAtHeight, uri, (chainIndex, height.value))

    def fetchBlocks(
        fromTs: TimeStamp,
        toTs: TimeStamp,
        uri: Uri
    ): Future[ArraySeq[ArraySeq[BlockEntityWithEvents]]] = {
      _send(getBlocksAndEvents, uri, api.model.TimeInterval(fromTs, toTs))
        .map(
          _.blocksAndEvents
            .map(_.map(blockAndEventsToEntities).toArraySeq)
            .toArraySeq
        )
    }

    def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]] =
      _send(listMempoolTransactions, uri, ())
        .map { utxs =>
          utxs.flatMap { utx =>
            utx.transactions.map { tx =>
              val inputs = InputAddressUtil
                .convertSameAsPrevious(tx.unsigned.inputs.toArraySeq)
                .map(protocolInputToInput)
              val outputs = tx.unsigned.fixedOutputs.map(protocolOutputToAssetOutput).toArraySeq
              txToUTx(
                tx,
                new GroupIndex(utx.fromGroup),
                new GroupIndex(utx.toGroup),
                inputs,
                outputs,
                TimeStamp.now()
              )
            }
          }.toArraySeq
        }

    def fetchSelfClique(): Future[SelfClique] =
      _send(getSelfClique, uri, ())

    def fetchChainParams(): Future[ChainParams] =
      _send(getChainParams, uri, ())

    private def fetchSelfCliqueAndChainParams(): Future[(SelfClique, ChainParams)] = {
      fetchSelfClique().flatMap { selfClique =>
        fetchChainParams().map(chainParams => (selfClique, chainParams))
      }
    }

    private def selfCliqueIndex(
        selfClique: SelfClique,
        chainParams: ChainParams,
        group: GroupIndex
    ): Either[ExplorerError, (InetAddress, Int)] = {
      if (chainParams.groupNumPerBroker <= 0) {
        Left(InvalidChainGroupNumPerBroker(chainParams.groupNumPerBroker))
      } else {
        Right(selfClique.peer(group)).map(node => (node.address, node.restPort))
      }
    }

    override def close(): Future[Unit] = {
      endpointSender.stop()
    }
  }

  def blockProtocolToInputEntities(block: api.model.BlockEntry): ArraySeq[InputEntity] = {
    val hash         = block.hash
    val mainChain    = false
    val transactions = block.transactions.toArraySeq.zipWithIndex
    val inputs =
      transactions.flatMap { case (tx, txOrder) =>
        InputAddressUtil.convertSameAsPrevious(tx.unsigned.inputs.toArraySeq).zipWithIndex.map {
          case (in, index) =>
            inputToEntity(in, hash, tx.unsigned.txId, block.timestamp, mainChain, index, txOrder)
        }
      }
    val contractInputs =
      transactions.flatMap { case (tx, txOrder) =>
        tx.contractInputs.toArraySeq.zipWithIndex.map { case (outputRef, index) =>
          val shiftIndex = index + tx.unsigned.inputs.length
          outputRefToInputEntity(
            outputRef,
            hash,
            tx.unsigned.txId,
            block.timestamp,
            mainChain,
            shiftIndex,
            txOrder
          )
        }
      }
    inputs ++ contractInputs
  }

  // scalastyle:off null
  def blockProtocolToOutputEntities(block: api.model.BlockEntry): ArraySeq[OutputEntity] = {
    val hash         = block.hash
    val mainChain    = false
    val transactions = block.transactions.toArraySeq.zipWithIndex
    // Genesis blocks don't have any transactions
    val coinbaseTxId =
      if (block.height == Height.genesis.value) null else block.transactions.last.unsigned.txId
    val outputs =
      transactions.flatMap { case (tx, txOrder) =>
        tx.unsigned.fixedOutputs.toArraySeq.zipWithIndex.map { case (out, index) =>
          val txId = tx.unsigned.txId
          outputToEntity(
            out.upCast(),
            hash,
            txId,
            index,
            block.timestamp,
            mainChain,
            txOrder,
            txId == coinbaseTxId
          )
        }
      }
    val generatedOutputs =
      transactions.flatMap { case (tx, txOrder) =>
        tx.generatedOutputs.toArraySeq.zipWithIndex.map { case (out, index) =>
          val shiftIndex = index + tx.unsigned.fixedOutputs.length
          outputToEntity(
            out,
            hash,
            tx.unsigned.txId,
            shiftIndex,
            block.timestamp,
            mainChain,
            txOrder,
            false
          )
        }
      }
    outputs ++ generatedOutputs
  }
  // scalastyle:on null

  def blockAndEventsToEntities(
      blockAndEvents: api.model.BlockAndEvents
  )(implicit groupSetting: GroupSetting): BlockEntityWithEvents = {
    BlockEntityWithEvents(
      blockProtocolToEntity(blockAndEvents.block),
      blockProtocolToEventEntities(blockAndEvents)
    )
  }

  // scalastyle:off null
  def blockProtocolToEntity(
      block: api.model.BlockEntry
  )(implicit groupSetting: GroupSetting): BlockEntity = {
    val hash         = block.hash
    val mainChain    = false
    val transactions = block.transactions.toArraySeq.zipWithIndex
    val chainFrom    = new GroupIndex(block.chainFrom)
    val chainTo      = new GroupIndex(block.chainTo)
    val inputs       = blockProtocolToInputEntities(block)
    val outputs      = blockProtocolToOutputEntities(block)
    // As defined in
    // https://github.com/alephium/alephium/blob/1e359e155b37c2afda6011cdc319d54ae8e4c059/protocol/src/main/scala/org/alephium/protocol/model/Block.scala#L35
    // Genesis blocks don't have any transactions
    val coinbaseTxId =
      if (block.height == Height.genesis.value) null else block.transactions.last.unsigned.txId
    BlockEntity(
      hash,
      block.timestamp,
      chainFrom,
      chainTo,
      Height.unsafe(block.height),
      block.deps.toArraySeq,
      transactions.map { case (tx, index) =>
        val coinbase = tx.unsigned.txId == coinbaseTxId
        txToEntity(tx, hash, block.timestamp, index, mainChain, chainFrom, chainTo, coinbase)
      },
      inputs,
      outputs,
      mainChain = mainChain,
      block.nonce,
      block.version,
      block.depStateHash,
      block.txsHash,
      block.target,
      computeHashRate(block.target)
    )
  }
  // scalastyle:on null

  def blockProtocolToEventEntities(
      blockAndEvents: api.model.BlockAndEvents
  ): ArraySeq[EventEntity] = {
    val block = blockAndEvents.block
    val hash  = block.hash
    val transactionAndInputAddress: Map[TransactionId, Option[Address]] =
      block.transactions
        .map { tx =>
          val address = InputAddressUtil.addressFromProtocolInputs(tx.unsigned.inputs.toArraySeq)
          (tx.unsigned.txId, address)
        }
        .iterator
        .to(Map)

    blockAndEvents.events.zipWithIndex.map { case (event, order) =>
      EventEntity.from(
        hash,
        event.txId,
        event.contractAddress,
        transactionAndInputAddress.getOrElse(event.txId, None),
        block.timestamp,
        event.eventIndex,
        event.fields.toArraySeq,
        order
      )
    }.toArraySeq
  }

  private def txToUTx(
      tx: api.model.TransactionTemplate,
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      inputs: ArraySeq[Input],
      outputs: ArraySeq[AssetOutput],
      timestamp: TimeStamp
  ): MempoolTransaction =
    MempoolTransaction(
      tx.unsigned.txId,
      chainFrom,
      chainTo,
      inputs,
      outputs,
      tx.unsigned.gasAmount,
      tx.unsigned.gasPrice,
      timestamp
    )

  private def txToEntity(
      tx: api.model.Transaction,
      blockHash: BlockHash,
      timestamp: TimeStamp,
      index: Int,
      mainChain: Boolean,
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      coinbase: Boolean
  ): TransactionEntity =
    TransactionEntity(
      tx.unsigned.txId,
      blockHash,
      timestamp,
      chainFrom,
      chainTo,
      tx.unsigned.gasAmount,
      tx.unsigned.gasPrice,
      index,
      mainChain,
      tx.scriptExecutionOk,
      if (tx.inputSignatures.isEmpty) None else Some(tx.inputSignatures.toArraySeq),
      if (tx.scriptSignatures.isEmpty) None else Some(tx.scriptSignatures.toArraySeq),
      coinbase
    )

  private def protocolInputToInput(input: api.model.AssetInput): Input = {
    Input(
      OutputRef(input.outputRef.hint, input.outputRef.key),
      Some(input.unlockScript),
      None,
      InputAddressUtil.addressFromProtocolInput(input),
      None,
      None
    )
  }

  private def inputToEntity(
      input: api.model.AssetInput,
      blockHash: BlockHash,
      txId: TransactionId,
      timestamp: TimeStamp,
      mainChain: Boolean,
      index: Int,
      txOrder: Int
  ): InputEntity = {
    InputEntity(
      blockHash,
      txId,
      timestamp,
      input.outputRef.hint,
      input.outputRef.key,
      Some(input.unlockScript),
      mainChain,
      index,
      txOrder,
      None,
      InputAddressUtil.addressFromProtocolInput(input),
      None,
      None
    )
  }

  private def outputRefToInputEntity(
      outputRef: api.model.OutputRef,
      blockHash: BlockHash,
      txId: TransactionId,
      timestamp: TimeStamp,
      mainChain: Boolean,
      index: Int,
      txOrder: Int
  ): InputEntity = {
    InputEntity(
      blockHash,
      txId,
      timestamp,
      outputRef.hint,
      outputRef.key,
      None,
      mainChain,
      index,
      txOrder,
      None,
      None,
      None,
      None
    )
  }

  private def protocolOutputToAssetOutput(output: api.model.FixedAssetOutput): AssetOutput = {
    val lockTime = output match {
      case asset: api.model.FixedAssetOutput if asset.lockTime.millis > 0 => Some(asset.lockTime)
      case _                                                              => None
    }
    AssetOutput(
      output.hint,
      output.key,
      output.attoAlphAmount.value,
      output.address,
      protocolTokensToTokens(output.tokens),
      lockTime,
      Some(output.message),
      None
    )
  }

  private def protocolTokensToTokens(tokens: AVector[api.model.Token]): Option[ArraySeq[Token]] = {
    if (tokens.isEmpty) {
      None
    } else {
      Some(
        ArraySeq.unsafeWrapArray(
          tokens
            .groupBy(_.id)
            .map { case (id, tokens) =>
              val amount = tokens.map(_.amount).fold(U256.Zero)(_ addUnsafe _)
              Token(id, amount)
            }
            .toArray
        )
      )
    }
  }

  // scalastyle:off method.length
  private def outputToEntity(
      output: api.model.Output,
      blockHash: BlockHash,
      txId: TransactionId,
      index: Int,
      timestamp: TimeStamp,
      mainChain: Boolean,
      txOrder: Int,
      coinbase: Boolean
  ): OutputEntity = {
    val lockTime = output match {
      case asset: api.model.AssetOutput if asset.lockTime.millis > 0 => Some(asset.lockTime)
      case _                                                         => None
    }

    val hint = output.address.lockupScript match {
      case asset: LockupScript.Asset  => Hint.ofAsset(asset.scriptHint)
      case contract: LockupScript.P2C => Hint.ofContract(contract.scriptHint)
    }

    val outputType: OutputEntity.OutputType = output match {
      case _: api.model.AssetOutput    => OutputEntity.Asset
      case _: api.model.ContractOutput => OutputEntity.Contract
    }

    val message = output match {
      case asset: api.model.AssetOutput => Some(asset.message)
      case _: api.model.ContractOutput  => None
    }

    val tokens = protocolTokensToTokens(output.tokens)

    OutputEntity(
      blockHash,
      txId,
      timestamp,
      outputType,
      hint.value,
      protocol.model.TxOutputRef.key(txId, index).value,
      output.attoAlphAmount.value,
      output.address,
      tokens,
      mainChain,
      lockTime,
      message,
      index,
      txOrder,
      coinbase,
      None,
      None
    )
  }

  // scalastyle:off magic.number
  def computeHashRate(targetBytes: ByteString)(implicit groupSetting: GroupSetting): BigInteger = {
    val target          = Target.unsafe(targetBytes)
    val blockTargetTime = Duration.ofSecondsUnsafe(64) // TODO add this to config
    HashRate.from(target, blockTargetTime)(groupSetting.groupConfig).value
  }
  // scalastyle:on magic.number
}
