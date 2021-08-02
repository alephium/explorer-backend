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

package org.alephium.explorer.persistence.dao

import scala.concurrent.ExecutionContext

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.api.model
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.Generators
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.DBRunner
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.InputSchema
import org.alephium.explorer.persistence.schema.OutputSchema
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.util.TimeStamp

class BlockDaoSpec extends AlephiumSpec with ScalaFutures with Generators with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "updateMainChainStatus correctly" in new Fixture {
    import config.profile.api._
    forAll(Gen.oneOf(blockEntities), arbitrary[Boolean]) {
      case (block, mainChainInput) =>
        blockDao.insert(block).futureValue
        blockDao.updateMainChainStatus(block.hash, mainChainInput).futureValue

        val fetchedBlock = blockDao.get(block.hash).futureValue.get
        fetchedBlock.hash is block.hash
        fetchedBlock.mainChain is mainChainInput

        val inputQuery  = inputsTable.filter(_.blockHash === block.hash).map(_.mainChain).result
        val outputQuery = outputsTable.filter(_.blockHash === block.hash).map(_.mainChain).result

        val inputs: Seq[Boolean]  = run(inputQuery).futureValue
        val outputs: Seq[Boolean] = run(outputQuery).futureValue

        inputs.size is block.inputs.size
        outputs.size is block.outputs.size
        (inputs ++ outputs).foreach(isMainChain => isMainChain is mainChainInput)
    }
  }

  trait Fixture extends InputSchema with OutputSchema with DatabaseFixture with DBRunner {
    override val config = databaseConfig
    val blockDao        = BlockDao(databaseConfig)
    val blockflow: Seq[Seq[model.BlockEntry]] =
      blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get
    val blocksProtocol: Seq[model.BlockEntry] = blockflow.flatten
    val blockEntities: Seq[BlockEntity]       = blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)
    blockDao.createTables().futureValue
  }
}
