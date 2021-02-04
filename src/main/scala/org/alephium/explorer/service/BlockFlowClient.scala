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

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import sttp.client._
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp._

import org.alephium.api
import org.alephium.api.Endpoints
import org.alephium.api.model.{ChainInfo, HashesAtHeight, SelfClique}
import org.alephium.explorer.{alfCoinConvertion, Hash}
import org.alephium.explorer.api.model.{Address, BlockEntry, GroupIndex, Height, Transaction}
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{GroupIndex => ProtocolGroupIndex, NetworkType, TxOutputRef}
import org.alephium.util.{Duration, Hex, TimeStamp}

trait BlockFlowClient {
  def fetchBlock(fromGroup: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntity]]

  def fetchChainInfo(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Either[String, ChainInfo]]

  def fetchHashesAtHeight(fromGroup: GroupIndex,
                          toGroup: GroupIndex,
                          height: Height): Future[Either[String, HashesAtHeight]]

  def fetchBlocksAtHeight(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)(
      implicit executionContext: ExecutionContext): Future[Either[Seq[String], Seq[BlockEntity]]] =
    fetchHashesAtHeight(fromGroup, toGroup, height).flatMap {
      case Right(hashesAtHeight) =>
        Future
          .sequence(
            hashesAtHeight.headers
              .map(hash => fetchBlock(fromGroup, new BlockEntry.Hash(hash)))
              .toSeq)
          .map { blocksEither =>
            val (errors, blocks) = blocksEither.partitionMap(identity)
            if (errors.nonEmpty) {
              Left(errors)
            } else {
              Right(blocks)
            }
          }
      case Left(error) => Future.successful(Left(Seq(error)))
    }

  def fetchSelfClique(): Future[Either[String, SelfClique]]
}

object BlockFlowClient {
  def apply(address: Uri, groupNum: Int, _networkType: NetworkType, blockflowFetchMaxAge: Duration)(
      implicit executionContext: ExecutionContext,
      actorSystem: ActorSystem): BlockFlowClient =
    new Impl(address, groupNum, _networkType, blockflowFetchMaxAge)

  private class Impl(address: Uri,
                     groupNum: Int,
                     _networkType: NetworkType,
                     val blockflowFetchMaxAge: Duration)(
      implicit executionContext: ExecutionContext,
      actorSystem: ActorSystem)
      extends BlockFlowClient
      with Endpoints {

    implicit lazy val groupConfig: GroupConfig = new GroupConfig { val groups = groupNum }
    implicit def networkType: NetworkType      = _networkType

    private implicit def groupIndexConversion(x: GroupIndex): ProtocolGroupIndex =
      ProtocolGroupIndex.unsafe(x.value)

    private val backend = AkkaHttpBackend.usingActorSystem(actorSystem)

    @SuppressWarnings(Array("org.wartremover.warts.ToString"))
    //TODO Introduce monad transformer helper for more readability
    def fetchBlock(fromGroup: GroupIndex,
                   hash: BlockEntry.Hash): Future[Either[String, BlockEntity]] =
      fetchSelfClique().flatMap {
        case Left(error) => Future.successful(Left(error))
        case Right(selfClique) =>
          selfCliqueIndex(selfClique, fromGroup) match {
            case Left(error) => Future.successful(Left(error))
            case Right(index) =>
              selfClique.peers
                .get(index)
                .map(peer => (peer.address, peer.restPort)) match {
                case None =>
                  Future.successful(
                    Left(s"cannot find peer for group $fromGroup (peers: ${selfClique.peers})"))
                case Some((peerAddress, restPort)) =>
                  val uri = s"http://${peerAddress.getHostAddress}:${restPort}"
                  backend
                    .send(getBlock.toSttpRequest(uri"${uri}").apply(hash.value))
                    .map(_.body match {
                      //TODO improve error management, here the decoding failure comes if the `networkType` is different from the blockflow server
                      case e: DecodeResult.Failure => Left(e.toString)
                      case DecodeResult.Value(res) =>
                        res.map(blockProtocolToEntity).left.map(_.message)
                    })
              }
          }
      }

    def fetchChainInfo(fromGroup: GroupIndex,
                       toGroup: GroupIndex): Future[Either[String, ChainInfo]] = {
      backend
        .send(
          getChainInfo.toSttpRequestUnsafe(uri"${address.toString}").apply((fromGroup, toGroup)))
        .map(_.body.left.map(_.message))
    }

    def fetchHashesAtHeight(fromGroup: GroupIndex,
                            toGroup: GroupIndex,
                            height: Height): Future[Either[String, HashesAtHeight]] =
      backend
        .send(
          getHashesAtHeight
            .toSttpRequestUnsafe(uri"${address.toString}")
            .apply((fromGroup, toGroup, height.value)))
        .map(_.body.left.map(_.message))

    def fetchSelfClique(): Future[Either[String, SelfClique]] =
      backend
        .send(getSelfClique.toSttpRequestUnsafe(uri"${address.toString}").apply(()))
        .map(_.body.left.map(_.message))
  }

  def blockProtocolToEntity(block: api.model.BlockEntry): BlockEntity = {
    val hash         = new BlockEntry.Hash(block.hash)
    val transactions = block.transactions.map(_.toSeq).getOrElse(Seq.empty)
    BlockEntity(
      hash,
      block.timestamp,
      GroupIndex.unsafe(block.chainFrom),
      GroupIndex.unsafe(block.chainTo),
      Height.unsafe(block.height),
      block.deps.map(new BlockEntry.Hash(_)).toSeq,
      transactions.map(txToEntity(_, hash, block.timestamp)),
      transactions.flatMap(tx => tx.inputs.toSeq.map(inputToEntity(_, hash, tx.id, false))),
      transactions.flatMap(tx =>
        tx.outputs.toSeq.zipWithIndex.map {
          case (out, index) => outputToEntity(out, hash, tx.id, index, block.timestamp, false)
      }),
      mainChain = false
    )
  }

  private def txToEntity(tx: api.model.Tx,
                         blockHash: BlockEntry.Hash,
                         timestamp: TimeStamp): TransactionEntity =
    TransactionEntity(
      new Transaction.Hash(tx.id),
      blockHash,
      timestamp
    )

  private def inputToEntity(input: api.model.Input,
                            blockHash: BlockEntry.Hash,
                            txId: Hash,
                            mainChain: Boolean): InputEntity =
    InputEntity(
      blockHash,
      new Transaction.Hash(txId),
      input.outputRef.scriptHint,
      input.outputRef.key,
      input.unlockScript.map(Hex.toHexString),
      mainChain
    )

  private def outputToEntity(output: api.model.Output,
                             blockHash: BlockEntry.Hash,
                             txId: Hash,
                             index: Int,
                             timestamp: TimeStamp,
                             mainChain: Boolean): OutputEntity = {
    OutputEntity(
      blockHash,
      new Transaction.Hash(txId),
      alfCoinConvertion(output.amount),
      new Address(output.address.toBase58),
      TxOutputRef.key(txId, index),
      timestamp,
      mainChain,
      output.lockTime.filter(_.millis > 0)
    )
  }

  private def selfCliqueIndex(selfClique: SelfClique, group: GroupIndex): Either[String, Int] = {
    if (selfClique.groupNumPerBroker <= 0) {
      Left(
        s"SelfClique.groupNumPerBroker ($selfClique.groupNumPerBroker) cannot be less or equal to zero")
    } else {
      Right(group.value / selfClique.groupNumPerBroker)
    }
  }
}
