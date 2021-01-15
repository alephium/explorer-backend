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

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.api.model.{ChainInfo, HashesAtHeight, Network}
import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height, TimeInterval}
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.protocol.model.{NetworkType}
import org.alephium.util.{AVector, Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class BlockFlowSyncServiceSpec extends AlephiumSpec with ScalaFutures with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  it should "start/sync/stop" in new Fixture {
    val blockFlowSyncService =
      BlockFlowSyncService(groupNum, syncPeriod = Duration.unsafe(2000), blockFlowClient, blockDao)

    checkBlocks(Seq.empty)

    blockFlowSyncService.start().futureValue is ()

    eventually(checkBlocks(blocks))

    eventually(checkMainChain(mainChain))

    blockFlowSyncService.stop().futureValue is ()

    databaseConfig.db.close
  }

  trait Fixture extends DatabaseFixture with Generators {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    def blockEntity(parent: Option[BlockEntity],
                    chainFrom: GroupIndex = GroupIndex.unsafe(0),
                    chainTo: GroupIndex   = GroupIndex.unsafe(0)): BlockEntity =
      blockEntityGen(groupNum, chainFrom, chainTo, parent).sample.get

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

    val block0  = blockEntity(None)
    val block1  = blockEntity(Some(block0))
    val block2  = blockEntity(Some(block1))
    val block3  = blockEntity(Some(block1))
    val block4  = blockEntity(Some(block3))
    val block5  = blockEntity(Some(block4))
    val block6  = blockEntity(Some(block5))
    val block7  = blockEntity(Some(block5))
    val block8  = blockEntity(Some(block7))
    val block9  = blockEntity(Some(block6))
    val block10 = blockEntity(Some(block6))
    val block11 = blockEntity(Some(block9))
    val block12 = blockEntity(Some(block10))
    val block13 = blockEntity(Some(block11))
    val block14 = blockEntity(Some(block8))

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

    val blockDao: BlockDao = BlockDao(databaseConfig)

    def blockEntities = blockFlowEntity.flatten

    def blocks: Seq[BlockEntry] = blockFlow.flatten

    val blockFlowClient: BlockFlowClient = new BlockFlowClient {
      def fetchBlock(from: GroupIndex, hash: BlockEntry.Hash): Future[Either[String, BlockEntity]] =
        Future.successful(blockEntities.find(_.hash === hash).toRight(s"$hash Not Found"))

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

      def fetchNetwork(): Future[Either[String, Network]] =
        Future.successful(
          Right(
            Network(NetworkType.Devnet)
          )
        )
    }

    def checkBlocks(blocksToCheck: Seq[BlockEntry]) = {
      val result = blockDao
        .listIncludingForks(
          TimeInterval.unsafe(TimeStamp.unsafe(0), TimeStamp.unsafe(Long.MaxValue)))
        .futureValue
        .map(_.hash)
        .toSet
      result is blocksToCheck.map(_.hash).toSet
    }

    def checkMainChain(mainChain: Seq[BlockEntry.Hash]) = {
      val result = blockDao
        .listMainChain(TimeInterval.unsafe(TimeStamp.unsafe(0), TimeStamp.unsafe(Long.MaxValue)))
        .futureValue
        .filter(block =>
          block.chainFrom == GroupIndex.unsafe(0) && block.chainTo == GroupIndex.unsafe(0))
        .map(_.hash)
        .toSet
      result is mainChain.toSet
    }
  }
  // scalastyle:on scalatest-matcher
}
