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
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.result.{OutputsFromTxQR, OutputsQR}
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.OutputSchema
import org.alephium.explorer.util.SlickTestUtil._

class OutputQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with Generators
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  "insert and ignore outputs" in {

    //forAll(Gen.listOf(updatedOutputEntityGen())) { existingAndUpdates =>
    val existingAndUpdates = Gen.listOf(updatedOutputEntityGen()).sample.get
    //fresh table
    run(OutputSchema.table.delete).futureValue

    val existing = existingAndUpdates.map(_._1) //existing outputs
    val ignored  = existingAndUpdates.map(_._2) //ignored outputs

    //insert existing
    run(insertOutputs(existing)).futureValue
    run(OutputSchema.table.result).futureValue.toSet is existing.toSet

    ////insert should ignore existing outputs
    run(insertOutputs(ignored)).futureValue
    run(OutputSchema.table.result).futureValue.toSet is existing.toSet
    //}
  }

  "outputsFromTxsNoJoin" should {
    "read from outputs table" when {
      "empty" in {
        //clear table
        run(OutputSchema.table.delete).futureValue
        run(OutputSchema.table.length.result).futureValue is 0

        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          //run query
          val hashes = outputs.map(output => (output.txHash, output.blockHash))
          val actual = run(OutputQueries.outputsFromTxsNoJoin(hashes)).futureValue

          //query output size is 0
          actual.size is 0
        }
      }

      "non-empty" in {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          //persist test-data
          run(OutputSchema.table.delete).futureValue
          run(OutputSchema.table ++= outputs).futureValue

          //run query
          val hashes = outputs.map(output => (output.txHash, output.blockHash))
          val actual = run(OutputQueries.outputsFromTxsNoJoin(hashes)).futureValue

          //expected query result
          val expected =
            outputs.map { entity =>
              OutputsFromTxQR(
                txHash      = entity.txHash,
                outputOrder = entity.outputOrder,
                outputType  = entity.outputType,
                hint        = entity.hint,
                key         = entity.key,
                amount      = entity.amount,
                address     = entity.address,
                tokens      = entity.tokens,
                lockTime    = entity.lockTime,
                message     = entity.message,
                spent       = entity.spentFinalized
              )
            }

          actual should contain theSameElementsAs expected
        }
      }
    }
  }

  "getOutputsQuery" should {
    "read outputs table" when {
      "empty" in {
        //table is empty
        run(OutputSchema.table.length.result).futureValue is 0

        forAll(outputEntityGen) { output =>
          //run query
          val actual =
            run(OutputQueries.getOutputsQuery(output.txHash, output.blockHash)).futureValue

          //query output size is 0
          actual.size is 0
        }
      }
    }

    "non-empty" in {
      forAll(Gen.listOf(outputEntityGen)) { outputs =>
        //no-need to clear the table for each iteration.
        run(OutputSchema.table ++= outputs).futureValue

        //run query for each output
        outputs foreach { output =>
          val actual =
            run(OutputQueries.getOutputsQuery(output.txHash, output.blockHash)).futureValue

          //expected query result
          val expected: OutputsQR =
            OutputsQR(
              outputType     = output.outputType,
              hint           = output.hint,
              key            = output.key,
              amount         = output.amount,
              address        = output.address,
              tokens         = output.tokens,
              lockTime       = output.lockTime,
              message        = output.message,
              spentFinalized = output.spentFinalized
            )

          actual.toList contains only(expected)
        }
      }
    }
  }

  "getTxnHash" should {
    "fetch tx_hash and use outputs_pk index" when {
      "main_chain = true" in {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          //no-need to clear the table for each iteration.
          run(OutputSchema.table ++= outputs).futureValue

          //run query for each output.
          outputs foreach { output =>
            val actual =
              run(getTxnHash(output.txHash.value)).futureValue

            run(getTxnHashSQL(output.txHash.value).explain()).futureValue
              .mkString("\n") should include("outputs_pk")

            if (output.mainChain) { //when main_chain = true expect hash to be fetched
              actual.toList contains only(output.txHash.value)
            } else { //else expect empty
              actual.size is 0
            }
          }
        }
      }
    }
  }

  "getBalanceActionOption" should {
    "return None" when {
      "address does not exist" in {
        val address = addressGen.sample getOrElse fail("Failed to sample address")
        run(getBalanceActionOption(address)).futureValue is ((None, None))
      }
    }
  }

  "index 'outputs_tx_hash_block_hash_idx'" should {
    "get used" when {
      "accessing column tx_hash" ignore {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          run(OutputSchema.table.delete).futureValue
          run(OutputSchema.table ++= outputs).futureValue

          outputs foreach { output =>
            val query =
              sql"""
                   |SELECT *
                   |FROM outputs
                   |where tx_hash = ${output.txHash}
                   |""".stripMargin

            val explain = run(query.explain()).futureValue.mkString("\n")

            explain should include("outputs_tx_hash_block_hash_idx")
          }
        }
      }
    }
  }

  "index 'outputs_pk'" should {
    "get used" when {
      "accessing column output_ref_key" ignore {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          run(OutputSchema.table.delete).futureValue
          run(OutputSchema.table ++= outputs).futureValue

          outputs foreach { output =>
            val query =
              sql"""
                   |SELECT *
                   |FROM outputs
                   |where key = ${output.key}
                   |""".stripMargin

            val explain = run(query.explain()).futureValue.mkString("\n")

            explain should include("outputs_pk")
          }
        }
      }
    }
  }
}
