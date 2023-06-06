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

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import sttp.model.Uri

import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel.chainIndexes
import org.alephium.explorer.GenCoreUtil.timestampMaxValue
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.error.ExplorerError
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._
import org.alephium.protocol.model.{BlockHash, ChainIndex, CliqueId, GroupIndex, NetworkId}
import org.alephium.util.{AVector, Duration, Hex, Service, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowSyncServiceSpec extends AlephiumFutureSpec with DatabaseFixtureForAll {

  "start/sync/stop" in new Fixture {
    using(Scheduler("")) { implicit scheduler =>
      checkBlocks(ArraySeq.empty)
      BlockFlowSyncService.start(ArraySeq(Uri("")), 1.second)

      chainOToO = ArraySeq(block0, block1, block2)
      eventually(checkMainChain(ArraySeq(block0.hash, block1.hash, block2.hash)))

      checkLatestHeight(2)

      chainOToO = ArraySeq(block0, block1, block3, block4)
      eventually(checkMainChain(ArraySeq(block0.hash, block1.hash, block3.hash, block4.hash)))

      checkLatestHeight(3)

      chainOToO = ArraySeq(block0, block1, block3, block4, block5, block7, block8, block14)
      eventually(
        checkMainChain(
          ArraySeq(block0.hash,
                   block1.hash,
                   block3.hash,
                   block4.hash,
                   block5.hash,
                   block7.hash,
                   block8.hash,
                   block14.hash)))

      checkLatestHeight(7)

      chainOToO = ArraySeq(block0, block1, block3, block4, block5, block6, block10, block12)
      eventually(
        checkMainChain(
          ArraySeq(block0.hash,
                   block1.hash,
                   block3.hash,
                   block4.hash,
                   block5.hash,
                   block6.hash,
                   block10.hash,
                   block12.hash)))

      chainOToO = ArraySeq(block0, block1, block3, block4, block5, block6, block9, block11, block13)
      eventually(checkMainChain(mainChain))

      checkLatestHeight(8)
    }
  }

  "fail if time range can't be build" in new Fixture {
    override implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {
      override def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo] =
        Future.successful(ChainInfo(0))

      override def fetchBlocksAtHeight(chainIndex: ChainIndex, height: Height)(
          implicit executionContext: ExecutionContext): Future[ArraySeq[BlockEntity]] =
        Future.successful(
          ArraySeq(blockEntityGen(chainIndex).sample.get.copy(timestamp = TimeStamp.unsafe(0))))
    }

    BlockDao.insertAll(blockEntities).futureValue

    BlockFlowSyncService
      .syncOnce(ArraySeq(Uri("")), new AtomicBoolean(true))
      .failed
      .futureValue is a[ExplorerError.RemoteTimeStampIsBeforeLocal]
  }
  trait Fixture {

    def t(l: Long)            = TimeStamp.unsafe(l)
    def s(l: Long)            = Duration.ofMillisUnsafe(l)
    def r(l1: Long, l2: Long) = (t(l1), t(l2))

    def blockEntity(
        parent: Option[BlockEntity],
        chainIndex: ChainIndex = ChainIndex(GroupIndex.Zero, GroupIndex.Zero)): BlockEntity =
      blockEntityWithParentGen(chainIndex, parent).sample.get

    //                    +---+                            +---+   +---+  +---+
    //                 +->+ 2 |                         +--> 9 +-->+ 11+->+ 13|
    //  +---+   +---+  |  ----+                  +---+  |  +---+   +---+  +---+
    //  | 0 +-->+ 1 +--+                      +->+ 6 +--+  +---+   +---+
    //  +---+   +---+  |  ----+  +---+  +---+ |  +---+  +--> 10+-->+ 12|
    //                 +->+ 3 +->+ 4 +->+ 5 +-+            +---+   +---+
    //                    +---+  +---+  +---+ |
    //                                        |  +---+     +---+   +---+
    //                                        +->+ 7 +---->+ 8 +-->+ 14 |
    //                                           +---+     +---+   +---+
    //    0       1        2       3       4       5         6       7      8

    def h(str: String) = BlockHash.unsafe(Hex.unsafe(str))

    val block0 = blockEntity(None)
      .copy(timestamp = TimeStamp.now())
      .copy(hash = h("0000000000000000000000000000000000000000000000000000000000000000"))
    val block1 = blockEntity(Some(block0))
      .copy(hash = h("1111111111111111111111111111111111111111111111111111111111111111"))
    val block2 = blockEntity(Some(block1))
      .copy(hash = h("2222222222222222222222222222222222222222222222222222222222222222"))
    val block3 = blockEntity(Some(block1))
      .copy(hash = h("3333333333333333333333333333333333333333333333333333333333333333"))
    val block4 = blockEntity(Some(block3))
      .copy(hash = h("4444444444444444444444444444444444444444444444444444444444444444"))
    val block5 = blockEntity(Some(block4))
      .copy(hash = h("5555555555555555555555555555555555555555555555555555555555555555"))
    val block6 = blockEntity(Some(block5))
      .copy(hash = h("6666666666666666666666666666666666666666666666666666666666666666"))
    val block7 = blockEntity(Some(block5))
      .copy(hash = h("7777777777777777777777777777777777777777777777777777777777777777"))
    val block8 = blockEntity(Some(block7))
      .copy(hash = h("8888888888888888888888888888888888888888888888888888888888888888"))
    val block9 = blockEntity(Some(block6))
      .copy(hash = h("9999999999999999999999999999999999999999999999999999999999999999"))
    val block10 = blockEntity(Some(block6))
      .copy(hash = h("1010101010101010101010101010101010101010101010101010101010101010"))
    val block11 = blockEntity(Some(block9))
      .copy(hash = h("1101101101101101101101101101101101101101101101101101101101101100"))
    val block12 = blockEntity(Some(block10))
      .copy(hash = h("1212121212121212121212121212121212121212121212121212121212121212"))
    val block13 = blockEntity(Some(block11))
      .copy(hash = h("1313131313131313131313131313131313131313131313131313131313131313"))
    val block14 = blockEntity(Some(block8))
      .copy(hash = h("1414141414141414141414141414141414141414141414141414141414141414"))

    val mainChain = ArraySeq(
      block0.hash,
      block1.hash,
      block3.hash,
      block4.hash,
      block5.hash,
      block6.hash,
      block9.hash,
      block11.hash,
      block13.hash
    )

    // format: off
    var chainOToO = ArraySeq(block0, block1, block2, block3, block4, block5, block6, block7, block8, block9, block10, block11, block12, block13, block14)
    // format: on

    val chains = chainIndexes.map { chainIndex =>
      ArraySeq(blockEntity(None, chainIndex))
    }.tail

    def blockFlowEntity: ArraySeq[ArraySeq[BlockEntity]] =
      chains :+ chainOToO

    def blockFlow: ArraySeq[ArraySeq[BlockEntry]] =
      blockEntitiesToBlockEntries(blockFlowEntity)

    implicit val blockCache: BlockCache = TestBlockCache()

    def blockEntities = ArraySeq.from(blockFlowEntity.flatten)

    def blocks: ArraySeq[BlockEntry] = blockFlow.flatten

    implicit val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      implicit val executionContext: ExecutionContext = implicitly
      def startSelfOnce(): Future[Unit]               = Future.unit
      def stopSelfOnce(): Future[Unit]                = Future.unit
      def subServices: ArraySeq[Service]              = ArraySeq.empty

      def fetchBlock(from: GroupIndex, hash: BlockHash): Future[BlockEntity] =
        Future.successful(blockEntities.find(_.hash === hash).get)

      def fetchBlockAndEvents(fromGroup: GroupIndex,
                              hash: BlockHash): Future[BlockEntityWithEvents] =
        Future.successful(
          BlockEntityWithEvents(blockEntities.find(_.hash === hash).get, ArraySeq.empty))

      def fetchBlocks(fromTs: TimeStamp,
                      toTs: TimeStamp,
                      uri: Uri): Future[ArraySeq[ArraySeq[BlockEntityWithEvents]]] =
        Future.successful(
          blockEntities
            .filter(b => b.timestamp >= fromTs && b.timestamp < toTs)
            .groupBy(b => (b.chainFrom, b.chainTo))
            .map(_._2)
            .map(_.distinctBy(_.height).sortBy(_.height))
            .map(_.map(b => BlockEntityWithEvents(b, ArraySeq.empty[EventEntity]))))

      def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo] =
        Future.successful(
          ChainInfo(
            blocks
              .filter(block =>
                block.chainFrom === chainIndex.from && block.chainTo === chainIndex.to)
              .map(_.height.value)
              .max))

      def fetchHashesAtHeight(chainIndex: ChainIndex, height: Height): Future[HashesAtHeight] =
        Future.successful(
          HashesAtHeight(AVector.from(blocks
            .filter(block =>
              block.chainFrom === chainIndex.from && block.chainTo === chainIndex.to && block.height === height)
            .map(_.hash))))

      def fetchSelfClique(): Future[SelfClique] =
        Future.successful(
          SelfClique(CliqueId.generate, AVector.empty, true, true)
        )

      def fetchChainParams(): Future[ChainParams] =
        Future.successful(
          ChainParams(NetworkId.AlephiumDevNet, 18, 1, 2)
        )

      def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]] =
        Future.successful(ArraySeq.empty)

      override def start(): Future[Unit] =
        Future.unit

      override def close(): Future[Unit] =
        Future.unit
    }

    def checkBlocks(blocksToCheck: ArraySeq[BlockEntry]) = {
      val result = BlockDao
        .listIncludingForks(TimeStamp.unsafe(0), timestampMaxValue)
        .futureValue
        .map(_.hash)

      result.size is blocksToCheck.size
      result.toSet is blocksToCheck.map(_.hash).toSet
    }

    def checkMainChain(mainChain: ArraySeq[BlockHash]) = {
      val result = BlockDao
        .listMainChain(Pagination.Reversible.unsafe(1, blocks.size))
        .futureValue
        ._1
        .filter(block => block.chainFrom == GroupIndex.Zero && block.chainTo == GroupIndex.Zero)
        .map(_.hash)
        .toSet
      result is mainChain.toSet
    }

    def checkLatestHeight(height: Int) = {
      eventually {
        BlockDao
          .latestBlocks()
          .futureValue
          .find {
            case (chainIndex, _) => chainIndex == ChainIndex.unsafe(0, 0)(groupSetting.groupConfig)
          }
          .get
          ._2
          .height
          .value is height
      }
    }
  }
  // scalastyle:on scalatest-matcher
}
