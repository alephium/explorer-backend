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

//scalastyle:off file.size.limit
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
import org.alephium.api.model.{
  CallContract,
  CallContractFailed,
  CallContractResult,
  CallContractSucceeded,
  ChainInfo,
  ChainParams,
  HashesAtHeight,
  MultipleCallContract,
  MultipleCallContractResult,
  SelfClique
}
import org.alephium.explorer.{Consensus, GroupSetting}
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.api.model._
import org.alephium.explorer.error.ExplorerError
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.InputAddressUtil
import org.alephium.http.EndpointSender
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.mining.HashRate
import org.alephium.protocol.model.{
  Address,
  BlockHash,
  ChainIndex,
  ContractId,
  GroupIndex,
  Target,
  TokenId,
  TransactionId
}
import org.alephium.util.{AVector, Hex, Service, TimeStamp, U256}

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

  def guessStdInterfaceId(address: Address.Contract): Future[Option[StdInterfaceId]]

  def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]]

  def fetchFungibleTokenMetadata(token: TokenId): Future[Option[FungibleTokenMetadata]]

  def fetchNFTMetadata(token: TokenId): Future[Option[NFTMetadata]]

  def fetchNFTCollectionMetadata(contract: Address.Contract): Future[Option[NFTCollectionMetadata]]

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

    def fetchBlockAndEvents(
        fromGroup: GroupIndex,
        hash: BlockHash
    ): Future[BlockEntityWithEvents] = {
      if (directCliqueAccess) {
        fetchSelfCliqueAndChainParams().flatMap { case (selfClique, chainParams) =>
          selfCliqueIndex(selfClique, chainParams, fromGroup) match {
            case Left(error) => Future.failed(new Throwable(error))
            case Right((nodeAddress, restPort)) =>
              val uri = Uri(nodeAddress.getHostAddress, restPort)
              _send(getBlockAndEvents, uri, hash).map(blockAndEventsToEntities)
          }
        }
      } else {
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

    def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] = {
      val address = Address.contract(ContractId.unsafe(token.value))
      guessStdInterfaceId(address)
    }

    def guessStdInterfaceId(address: Address.Contract): Future[Option[StdInterfaceId]] = {
      _send(contractState, uri, address)
        .map { rawState =>
          rawState.immFields.lastOption match {
            case Some(api.model.ValByteVec(bytes)) =>
              val data = Hex.toHexString(bytes)
              if (data.startsWith(interfaceIdPrefix)) {
                Some(StdInterfaceId.from(data.drop(interfaceIdPrefix.length)))
              } else {
                Some(StdInterfaceId.NonStandard)
              }
            case _ => Some(StdInterfaceId.NonStandard)
          }
        }
        .recoverWith { error =>
          logger.debug(s"Cannot fetch std interface id of $address")
          Future.successful(None)
        }
    }

    private def fetchTokenMetadata[A](token: TokenId, nbArgs: Int)(
        f: MultipleCallContractResult => Option[A]
    ): Future[Option[A]] = {
      val address = Address.contract(ContractId.unsafe(token.value))
      fetchMetadata(address, nbArgs)(f)
    }

    def fetchMetadata[A](address: Address.Contract, nbArgs: Int)(
        f: MultipleCallContractResult => Option[A]
    ): Future[Option[A]] = {
      val group = address.groupIndex(groupSetting.groupConfig)
      val calls = AVector.from(0 to (nbArgs - 1)).map { index =>
        CallContract(group = group.value, address = address, methodIndex = index)
      }

      _send[MultipleCallContract, MultipleCallContractResult](
        multiCallContract,
        uri,
        MultipleCallContract(calls)
      ).map { result =>
        if (result.results.length < nbArgs) {
          None
        } else {
          f(result)
        }
      }.recoverWith { _ =>
        logger.debug(s"Cannot fetch metadata of $address")
        Future.successful(None)
      }
    }

    def fetchFungibleTokenMetadata(token: TokenId): Future[Option[FungibleTokenMetadata]] = {
      fetchTokenMetadata[FungibleTokenMetadata](token, 3) { result =>
        extractFungibleTokenMetadata(token, result)
      }
    }

    def fetchNFTCollectionMetadata(
        contract: Address.Contract
    ): Future[Option[NFTCollectionMetadata]] = {
      fetchMetadata[NFTCollectionMetadata](contract, 1) { result =>
        extractNFTCollectionMetadata(contract, result)
      }
    }

    def fetchNFTMetadata(token: TokenId): Future[Option[NFTMetadata]] = {
      fetchTokenMetadata[NFTMetadata](token, 2) { result =>
        extractNFTMetadata(token, result)
      }
    }

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

  val interfaceIdPrefix = "414c5048"

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def extractVal(
      result: CallContractResult,
      access: (AVector[api.model.Val] => Option[api.model.Val]) = _.headOption
  ): Option[api.model.Val] = {
    result match {
      case success: CallContractSucceeded => access(success.returns)
      case _: CallContractFailed          => None
    }
  }

  def valToU256(value: api.model.Val): Option[U256] =
    value match {
      case api.model.ValU256(u256) =>
        Some(u256)
      case _ => None
    }

  def valToString(value: api.model.Val): Option[String] =
    value match {
      case api.model.ValByteVec(bytes) =>
        Some(bytes.utf8String)
      case _ => None
    }

  def valToContractId(value: api.model.Val): Option[ContractId] = {
    value match {
      case api.model.ValByteVec(bytes) =>
        ContractId.from(bytes)
      case _ => None
    }
  }

  def valToAddress(value: api.model.Val): Option[Address] = {
    value match {
      case api.model.ValAddress(address) =>
        Some(address)
      case api.model.ValByteVec(bytes) =>
        ContractId.from(bytes).map(Address.contract)
      case _ => None
    }
  }

  def extractFungibleTokenMetadata(
      token: TokenId,
      result: MultipleCallContractResult
  ): Option[FungibleTokenMetadata] = {
    for {
      symbol   <- extractVal(result.results(0)).flatMap(valToString)
      name     <- extractVal(result.results(1)).flatMap(valToString)
      decimals <- extractVal(result.results(2)).flatMap(valToU256)
    } yield {
      FungibleTokenMetadata(
        token,
        symbol,
        name,
        decimals
      )
    }
  }

  def extractNFTCollectionMetadata(
      contract: Address.Contract,
      result: MultipleCallContractResult
  ): Option[NFTCollectionMetadata] = {
    for {
      collectionUri <- extractVal(result.results(0)).flatMap(valToString)
    } yield {
      NFTCollectionMetadata(
        contract,
        collectionUri
      )
    }
  }
  def extractNFTMetadata(
      token: TokenId,
      result: MultipleCallContractResult
  ): Option[NFTMetadata] = {
    for {
      tokenUri     <- extractVal(result.results(0)).flatMap(valToString)
      collectionId <- extractVal(result.results(1)).flatMap(valToContractId)
      nftIndex     <- extractVal(result.results(1), _.tail.headOption).flatMap(valToU256)
    } yield {
      NFTMetadata(
        token,
        tokenUri,
        collectionId,
        nftIndex
      )
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
            inputToEntity(
              in,
              hash,
              tx.unsigned.txId,
              block.timestamp,
              mainChain,
              index,
              txOrder,
              contractInput = false
            )
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
            txOrder,
            contractInput = true
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
            coinbase = txId == coinbaseTxId,
            fixedOutput = true
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
            coinbase = false,
            fixedOutput = false
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
    val ghostUncles = block.ghostUncles.toArraySeq.map { ghostUncle =>
      GhostUncle(ghostUncle.blockHash, ghostUncle.miner)
    }

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
      computeHashRate(block.target, block.timestamp),
      ghostUncles
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
      tx.unsigned.version,
      tx.unsigned.networkId,
      tx.unsigned.scriptOpt.map(_.value),
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
      None,
      contractInput = false
    )
  }

  private def inputToEntity(
      input: api.model.AssetInput,
      blockHash: BlockHash,
      txId: TransactionId,
      timestamp: TimeStamp,
      mainChain: Boolean,
      index: Int,
      txOrder: Int,
      contractInput: Boolean
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
      None,
      contractInput = contractInput
    )
  }

  private def outputRefToInputEntity(
      outputRef: api.model.OutputRef,
      blockHash: BlockHash,
      txId: TransactionId,
      timestamp: TimeStamp,
      mainChain: Boolean,
      index: Int,
      txOrder: Int,
      contractInput: Boolean
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
      None,
      contractInput = contractInput
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
      None,
      fixedOutput = true
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
      coinbase: Boolean,
      fixedOutput: Boolean
  ): OutputEntity = {
    val lockTime = output match {
      case asset: api.model.AssetOutput if asset.lockTime.millis > 0 => Some(asset.lockTime)
      case _                                                         => None
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
      output.hint,
      output.key,
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
      None,
      fixedOutput
    )
  }

  def computeHashRate(targetBytes: ByteString, timestamp: TimeStamp)(implicit
      groupSetting: GroupSetting
  ): BigInteger = {
    val target = Target.unsafe(targetBytes)
    HashRate.from(target, Consensus.blockTargetTime(timestamp))(groupSetting.groupConfig).value
  }
}
