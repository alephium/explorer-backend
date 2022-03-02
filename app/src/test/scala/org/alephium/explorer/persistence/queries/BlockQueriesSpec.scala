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

package org.alephium.explorer.persistence.queries

import scala.concurrent.ExecutionContext

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.schema._

class BlockQueriesSpec extends AlephiumSpec with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1000, Minutes))

  it should "insert and ignore block_headers" in new Fixture {

    import config.profile.api._

    forAll(Gen.listOf(updatedBlockHeaderGen())) { existingAndUpdates =>
      //fresh table
      run(blockHeadersTable.delete).futureValue

      val existing = existingAndUpdates.map(_._1) //existing blocks
      val ingored  = existingAndUpdates.map(_._2) //ingored blocks

      val query = queries.upsertBlockHeaders(existing)

      //upsert existing
      run(query).futureValue is existing.size
      run(queries.blockHeadersTable.result).futureValue should contain allElementsOf existing

      //upsert should ingore existing inputs
      run(queries.upsertBlockHeaders(ingored)).futureValue is 0
      run(queries.blockHeadersTable.result).futureValue should contain allElementsOf existing
    }
  }

  it should "insert deps, transactions, inputs, outputs, block_headers" in new Fixture {

    import config.profile.api._

    forAll(Gen.listOf(genBlockEntityWithOptionalParent().map(_._1))) {
      case entities =>
        //clear all tables
        run(blockHeadersTable.delete).futureValue
        run(transactionsTable.delete).futureValue
        run(inputsTable.delete).futureValue
        run(outputsTable.delete).futureValue
        run(blockDepsTable.delete).futureValue

        //execute upsert on blocks and expect all tables get inserted
        run(queries.upsertBlockEntity(entities, groupNum)).futureValue is entities.size

        //check block_headers table
        val actualBlockHeaders = run(blockHeadersTable.result).futureValue
        val expectBlockHeaders = entities.map(_.toBlockHeader(groupNum))
        actualBlockHeaders should contain allElementsOf expectBlockHeaders

        //check transactions table
        val actualTransactions   = run(transactionsTable.result).futureValue
        val expectedTransactions = entities.flatMap(_.transactions)
        actualTransactions should contain allElementsOf expectedTransactions

        //check inputs table
        val actualInputs   = run(inputsTable.result).futureValue
        val expectedInputs = entities.flatMap(_.inputs)
        actualInputs should contain allElementsOf expectedInputs

        ////check outputs table
        //val actualOutputs   = run(outputsTable.result).futureValue
        //val expectedOutputs = entities.flatMap(_.outputs)
        //actualOutputs should contain allElementsOf expectedOutputs

        //check block_deps table
        val actualDeps        = run(blockDepsTable.result).futureValue
        val expectedBlockDeps = entities.flatMap(_.toBlockDepEntities())
        actualDeps should contain allElementsOf expectedBlockDeps

      //There is no need for testing updates here since updates are already
      //tested each table's individual test-cases.
    }
  }

  trait Fixture
      extends DatabaseFixture
      with DBRunner
      with Generators
      with BlockHeaderSchema
      with TransactionSchema
      with InputSchema
      with OutputSchema
      with BlockDepsSchema {
    val config: DatabaseConfig[JdbcProfile] = databaseConfig

    class Queries(val config: DatabaseConfig[JdbcProfile])(
        implicit val executionContext: ExecutionContext)
        extends BlockQueries

    val queries = new Queries(databaseConfig)

  }
}
