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

package org.alephium.explorer.service.sync

import org.alephium.explorer.{AlephiumSpec, GroupSetting}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.service.sync.SimersSyncPlayground._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model.GroupIndex
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.protocol.config.GroupConfig
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

class SimersSyncPlaygroundSpec extends AlephiumSpec with DatabaseFixtureForEach with Matchers with ScalaFutures with Eventually with MockFactory {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(50, Seconds))
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  implicit val groupSetting: GroupSetting = GroupSetting(4)
  implicit val groupConfig: GroupConfig = groupSetting.groupConfig

  //TODO: Need a test function to easily create two or more blocks that have parent/child relationship.
  //      Setting chainFrom and chainTo to be 0, to avoid getting assumption failure.
  def createParentChildBlocks(parentMainChain: Boolean = true, childMainChain: Boolean = true): Gen[(BlockEntity, BlockEntity)] =
    blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), None) flatMap {
      parent =>
        val mainChainParent = parent.updateMainChain(parentMainChain)
        blockEntityGen(parent.chainFrom, parent.chainTo, Some(mainChainParent)) map {
          child =>
            (mainChainParent, child.updateMainChain(childMainChain))
        }
    }

  "findMissingParentBlocks" should {
    "return empty" when {
      "the input is empty" in {
        implicit val client: BlockFlowClient =
          mock[BlockFlowClient]

        findOutOfSyncBlocks(ArraySeq.empty).futureValue shouldBe empty
      }

      "the input block is the first block i.e. height = 0" in {
        forAll(blockEntityGen(None)) {
          entity =>
            implicit val client: BlockFlowClient =
              mock[BlockFlowClient]

            findOutOfSyncBlocks(ArraySeq(entity)).futureValue shouldBe empty
        }
      }

      "input main_chain block already exists in database" in {
        forAll(createParentChildBlocks()) {
          case (parent, child) =>
            val parentAndChildHeaders = Seq(parent, child).map(_.toBlockHeader(groupSetting.groupNum))
            DBRunner.run(BlockHeaderSchema.table ++= parentAndChildHeaders).futureValue

            implicit val client: BlockFlowClient =
              mock[BlockFlowClient]

            findOutOfSyncBlocks(ArraySeq(child)).futureValue shouldBe empty
        }
      }
    }

    "return insert action" when {
      "the parent block does not exists" in {
        forAll(createParentChildBlocks()) {
          case (parent, child) =>
            DBRunner.run(BlockHeaderSchema.table += child.toBlockHeader(groupSetting.groupNum)).futureValue

            implicit val client: BlockFlowClient =
              mock[BlockFlowClient]

            (client.fetchBlock _).expects(parent.chainFrom, parent.hash) returns Future.successful(parent)

            findOutOfSyncBlocks(ArraySeq(child)).futureValue should contain only SyncLocalAction.InsertNewBlock(parent)
        }
      }
    }

    "return updateMainChain action" when {
      "the parent block exists but is not main_chain" in {
        forAll(createParentChildBlocks(parentMainChain = false)) {
          case (parent, child) =>
            val parentAndChildHeaders = Seq(parent, child).map(_.toBlockHeader(groupSetting.groupNum))
            DBRunner.run(BlockHeaderSchema.table ++= parentAndChildHeaders).futureValue

            implicit val client: BlockFlowClient =
              mock[BlockFlowClient]

            findOutOfSyncBlocks(ArraySeq(child)).futureValue should contain only
              SyncLocalAction.UpdateMainChain(
                hash = parent.hash,
                height = parent.height,
                child = child
              )
        }
      }
    }
  }
}
