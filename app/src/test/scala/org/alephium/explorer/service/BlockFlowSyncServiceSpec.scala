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
import scala.concurrent.duration._

import akka.http.scaladsl.model.Uri
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.{AlephiumSpec, BlockHash, Generators, GroupSetting}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TestUtils._
import org.alephium.protocol.model.{ChainIndex, CliqueId, NetworkId}
import org.alephium.util.{AVector, Duration, Hex, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowSyncServiceSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with Generators
    with ScalaFutures
    with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(50, Seconds))

  it should "build timestamp range" in new Fixture {

    BlockFlowSyncService.buildTimestampRange(t(0), t(5), s(1)) is
      Seq(r(0, 1), r(2, 3), r(4, 5))

    BlockFlowSyncService.buildTimestampRange(t(0), t(5), s(2)) is
      Seq(r(0, 2), r(3, 5))

    BlockFlowSyncService.buildTimestampRange(t(0), t(6), s(2)) is
      Seq(r(0, 2), r(3, 5), r(6, 6))

    BlockFlowSyncService.buildTimestampRange(t(0), t(7), s(2)) is
      Seq(r(0, 2), r(3, 5), r(6, 7))

    BlockFlowSyncService.buildTimestampRange(t(1), t(1), s(1)) is
      Seq.empty

    BlockFlowSyncService.buildTimestampRange(t(1), t(0), s(1)) is
      Seq.empty

    BlockFlowSyncService.buildTimestampRange(t(0), t(1), s(0)) is
      Seq.empty
  }

  it should "fetch and build timestamp range" in new Fixture {

    def th(ts: TimeStamp, height: Int) = Future.successful(Option((ts, height)))

    BlockFlowSyncService
      .fetchAndBuildTimeStampRange(s(10), s(5), th(t(20), 5), th(t(40), 8))
      .futureValue is ((Seq(r(16, 26), r(27, 37), r(38, 41)), 3))

    BlockFlowSyncService
      .fetchAndBuildTimeStampRange(s(10), s(5), Future.successful(None), th(t(40), 8))
      .futureValue is ((Seq.empty, 0))

    BlockFlowSyncService
      .fetchAndBuildTimeStampRange(s(10), s(5), th(t(20), 5), Future.successful(None))
      .futureValue is ((Seq.empty, 0))
  }

  it should "start/sync/stop" in new Fixture {
    using(Scheduler("")) { implicit scheduler =>
      checkBlocks(Seq.empty)
      BlockFlowSyncService.start(Seq(""), 1.second)

      chainOToO = Seq(block0, block1, block2)
      eventually(checkMainChain(Seq(block0.hash, block1.hash, block2.hash)))

      checkLatestHeight(2)

      chainOToO = Seq(block0, block1, block3, block4)
      eventually(checkMainChain(Seq(block0.hash, block1.hash, block3.hash, block4.hash)))

      checkLatestHeight(3)

      chainOToO = Seq(block0, block1, block3, block4, block5, block7, block8, block14)
      eventually(
        checkMainChain(
          Seq(block0.hash,
              block1.hash,
              block3.hash,
              block4.hash,
              block5.hash,
              block7.hash,
              block8.hash,
              block14.hash)))

      checkLatestHeight(7)

      chainOToO = Seq(block0, block1, block3, block4, block5, block6, block10, block12)
      eventually(
        checkMainChain(
          Seq(block0.hash,
              block1.hash,
              block3.hash,
              block4.hash,
              block5.hash,
              block6.hash,
              block10.hash,
              block12.hash)))

      chainOToO = Seq(block0, block1, block3, block4, block5, block6, block9, block11, block13)
      eventually(checkMainChain(mainChain))

      checkLatestHeight(8)

      databaseConfig.db.close
    }
  }

  trait Fixture {

    def t(l: Long)            = TimeStamp.unsafe(l)
    def s(l: Long)            = Duration.ofMillisUnsafe(l)
    def r(l1: Long, l2: Long) = (t(l1), t(l2))

    implicit val executionContext: ExecutionContext = ExecutionContext.global

    def blockEntity(parent: Option[BlockEntity],
                    chainFrom: GroupIndex = GroupIndex.unsafe(0),
                    chainTo: GroupIndex   = GroupIndex.unsafe(0)): BlockEntity =
      blockEntityGen(chainFrom, chainTo, parent).sample.get

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

    def h(str: String) = new BlockEntry.Hash(BlockHash.unsafe(Hex.unsafe(str)))

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

    val mainChain = Seq(
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
    var chainOToO = Seq(block0, block1, block2, block3, block4, block5, block6, block7, block8, block9, block10, block11, block12, block13, block14)
    // format: on

    val chains = chainIndexes.map {
      case (from, to) =>
        Seq(blockEntity(None, from, to))
    }.tail

    def blockFlowEntity: Seq[Seq[BlockEntity]] =
      chains :+ chainOToO

    def blockFlow: Seq[Seq[BlockEntry]] =
      blockEntitiesToBlockEntries(blockFlowEntity)

    implicit val groupSettings: GroupSetting = GroupSetting(groupNum)
    implicit val blockCache: BlockCache      = BlockCache()

    def blockEntities = blockFlowEntity.flatten

    def blocks: Seq[BlockEntry] = blockFlow.flatten

    implicit val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      def fetchBlock(from: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntity]] =
        Future.successful(blockEntities.find(_.hash === hash).toRight(s"$hash Not Found"))

      def fetchBlocks(fromTs: TimeStamp,
                      toTs: TimeStamp,
                      uri: Uri): Future[Either[String, Seq[Seq[BlockEntity]]]] =
        Future.successful(
          Right(
            blockEntities
              .filter(b => b.timestamp >= fromTs && b.timestamp < toTs)
              .groupBy(b => (b.chainFrom, b.chainTo))
              .toSeq
              .map(_._2)
              .map(_.distinctBy(_.height).sortBy(_.height))))

      def fetchChainInfo(from: GroupIndex, to: GroupIndex): Future[Either[String, ChainInfo]] =
        Future.successful(
          Right(
            ChainInfo(
              blocks
                .filter(block => block.chainFrom === from && block.chainTo === to)
                .map(_.height.value)
                .max)))

      def fetchHashesAtHeight(from: GroupIndex,
                              to: GroupIndex,
                              height: Height): Future[Either[String, HashesAtHeight]] =
        Future.successful(
          Right(
            HashesAtHeight(
              AVector.from(blocks
                .filter(block =>
                  block.chainFrom === from && block.chainTo === to && block.height === height)
                .map(_.hash.value)))))

      def fetchSelfClique(): Future[Either[String, SelfClique]] =
        Future.successful(
          Right(
            SelfClique(CliqueId.generate, AVector.empty, true, true)
          )
        )

      def fetchChainParams(): Future[Either[String, ChainParams]] =
        Future.successful(
          Right(
            ChainParams(NetworkId.AlephiumDevNet, 18, 1, 2)
          )
        )

      def fetchUnconfirmedTransactions(
          uri: Uri): Future[Either[String, Seq[UnconfirmedTransaction]]] =
        Future.successful(Right(Seq.empty))
    }

    def checkBlocks(blocksToCheck: Seq[BlockEntry]) = {
      val result = BlockDao
        .listIncludingForks(TimeStamp.unsafe(0), timestampMaxValue)
        .futureValue
        .map(_.hash)

      result.size is blocksToCheck.size
      result.toSet is blocksToCheck.map(_.hash).toSet
    }

    def checkMainChain(mainChain: Seq[BlockEntry.Hash]) = {
      val result = BlockDao
        .listMainChainSQLCached(Pagination.unsafe(0, blocks.size))
        .futureValue
        ._1
        .filter(block =>
          block.chainFrom == GroupIndex.unsafe(0) && block.chainTo == GroupIndex.unsafe(0))
        .map(_.hash)
        .toSet
      result is mainChain.toSet
    }

    def checkLatestHeight(height: Int) = {
      eventually {
        BlockDao
          .latestBlocks()
          .futureValue
          .find { case (chainIndex, _) => chainIndex == ChainIndex.unsafe(0, 0) }
          .get
          ._2
          .height
          .value is height
      }
    }
  }
  // scalastyle:on scalatest-matcher
}
