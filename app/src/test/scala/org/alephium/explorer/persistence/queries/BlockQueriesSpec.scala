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
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.tag.IndexCheck
import org.alephium.explorer.util.SlickTestUtil._

class BlockQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with Generators
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1000, Minutes))

  "insert and ignore block_headers" in {

    forAll(Gen.listOf(updatedBlockHeaderGen())) { existingAndUpdates =>
      //fresh table
      run(BlockHeaderSchema.table.delete).futureValue

      val existing = existingAndUpdates.map(_._1) //existing blocks
      val ignored  = existingAndUpdates.map(_._2) //ignored blocks

      val query = BlockQueries.insertBlockHeaders(existing)

      //insert existing
      run(query).futureValue is existing.size
      run(BlockHeaderSchema.table.result).futureValue should contain allElementsOf existing

      //insert should ignore existing inputs
      run(BlockQueries.insertBlockHeaders(ignored)).futureValue is 0
      run(BlockHeaderSchema.table.result).futureValue should contain allElementsOf existing
    }
  }

  "insert deps, transactions, inputs, outputs, block_headers" in {

    forAll(Gen.listOf(genBlockEntityWithOptionalParent().map(_._1))) { entities =>
      //clear all tables
      run(BlockHeaderSchema.table.delete).futureValue
      run(TransactionSchema.table.delete).futureValue
      run(InputSchema.table.delete).futureValue
      run(OutputSchema.table.delete).futureValue
      run(BlockDepsSchema.table.delete).futureValue

      //execute insert on blocks and expect all tables get inserted
      run(BlockQueries.insertBlockEntity(entities, groupNum)).futureValue is ()

      //check block_headers table
      val actualBlockHeaders = run(BlockHeaderSchema.table.result).futureValue
      val expectBlockHeaders = entities.map(_.toBlockHeader(groupNum))
      actualBlockHeaders should contain allElementsOf expectBlockHeaders

      //check transactions table
      val actualTransactions   = run(TransactionSchema.table.result).futureValue
      val expectedTransactions = entities.flatMap(_.transactions)
      actualTransactions should contain allElementsOf expectedTransactions

      //check inputs table
      val actualInputs   = run(InputSchema.table.result).futureValue
      val expectedInputs = entities.flatMap(_.inputs)
      actualInputs should contain allElementsOf expectedInputs

      ////check outputs table
      //val actualOutputs   = run(outputsTable.result).futureValue
      //val expectedOutputs = entities.flatMap(_.outputs)
      //actualOutputs should contain allElementsOf expectedOutputs

      //check block_deps table
      val actualDeps        = run(BlockDepsSchema.table.result).futureValue
      val expectedBlockDeps = entities.flatMap(_.toBlockDepEntities())
      actualDeps should contain allElementsOf expectedBlockDeps

      //There is no need for testing updates here since updates are already
      //tested each table's individual test-cases.
    }
  }

  "getBlockHeaderAction" should {
    "search BlockHeader" when {
      "hash exists" in {
        forAll(blockHeaderGen) { expectedHeader =>
          //clear table
          run(BlockHeaderSchema.table.delete).futureValue
          //insert a block_header
          run(BlockHeaderSchema.table += expectedHeader).futureValue is 1
          //read the inserted block_header
          val actualHeader = run(BlockQueries.getBlockHeaderAction(expectedHeader.hash)).futureValue
          //read block_header should be the same as the inserted block_header
          actualHeader should contain(expectedHeader)
        }
      }

      "hash does not exist" in {
        //clear table
        run(BlockHeaderSchema.table.delete).futureValue

        forAll(blockEntryHashGen) { hash =>
          //table is empty
          run(BlockHeaderSchema.table.length.result).futureValue is 0
          //expect None
          run(BlockQueries.getBlockHeaderAction(hash)).futureValue is None
        }
      }
    }
  }

  "listMainChainHeadersWithTxnNumberSQLBuilder" should {
    "use block_headers_full_index" taggedAs IndexCheck in {
      forAll(Gen.listOf(blockHeaderGen)) { headers =>
        //persist test-data
        run(BlockHeaderSchema.table.delete).futureValue
        run(BlockHeaderSchema.table ++= headers).futureValue

        //build explain query
        val explain =
          BlockQueries
            .listMainChainHeadersWithTxnNumberSQLBuilder(Pagination.unsafe(1, 10))
            .explain() //flatten so we get single String instead of Vector[String]

        //run explain query
        val explainResult =
          run(explain).futureValue

        //check the query is using the index named `block_headers_full_index` in `block_headers` table
        explainResult.mkString should
          include("Index Scan using block_headers_full_index on block_headers")
      }
    }
  }

  "mainChainQuery" should {

    //Build database state for headers and run explain on mainChainQuery
    def runExplain(headers: Seq[BlockHeader]) = {
      //Clear table persist test-data
      run(BlockHeaderSchema.table.delete).futureValue
      run(BlockHeaderSchema.table ++= headers).futureValue

      //build explain query for mainChainQuery
      run(BlockQueries.mainChainQuery.result.explainFlatten()).futureValue
    }

    "use block_headers_main_chain_idx" when {
      "data size is large" taggedAs IndexCheck in {
        /*
         * Flaky test. Postgres randomly (not very often) chooses not to use `block_headers_main_chain_idx`,
         * even if more data gets persisted after the first query, Postgres still does sequential scan
         * after multiple attempts of writing more data.
         *
         * Query planner uses sequential scan:
         * Seq Scan on block_headers  (cost=0.00..1652.67 rows=3606 width=235)
         *    Filter: main_chain
         */
        forAll(Gen.listOfN(20000, blockHeaderGen)) { headers =>
          runExplain(headers) should include("Index Scan on block_headers_main_chain_idx")
        }
      }
    }

    "not use block_headers_main_chain_idx" when {
      "data size is small" taggedAs IndexCheck in {
        val headers = Gen.choose(0, 10) flatMap (Gen.listOfN(_, blockHeaderGen))

        forAll(headers) { headers =>
          runExplain(headers) should not include "block_headers_main_chain_idx"
        }
      }
    }
  }
}
