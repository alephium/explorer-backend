// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.result.{OutputFromTxQR, OutputQR}
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.OutputSchema
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.util.TimeStamp

class OutputQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  "insert and ignore outputs" in {

    forAll(Gen.listOf(updatedOutputEntityGen())) { existingAndUpdates =>
      // fresh table
      exec(OutputSchema.table.delete)

      val existing = existingAndUpdates.map(_._1) // existing outputs
      val ignored  = existingAndUpdates.map(_._2) // ignored outputs

      // insert existing
      exec(insertOutputs(existing))
      exec(OutputSchema.table.result).toSet is existing.toSet

      //// insert should ignore existing outputs
      exec(insertOutputs(ignored))
      exec(OutputSchema.table.result).toSet is existing.toSet
    }
  }

  "insert finalized outputs" in {
    forAll(Gen.listOf(finalizedOutputEntityGen)) { outputs =>
      // fresh table
      exec(OutputSchema.table.delete)

      exec(insertOutputs(outputs))
      exec(OutputSchema.table.result).toSet is outputs.toSet
    }
  }

  "outputsFromTxs" should {
    "read from outputs table" when {
      "empty" in {
        // clear table
        exec(OutputSchema.table.delete)
        exec(OutputSchema.table.length.result) is 0

        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          // exec
          val hashes = outputs.map(output => (output.txHash, output.blockHash))
          val actual = exec(OutputQueries.outputsFromTxs(hashes))

          // query output size is 0
          actual.size is 0
        }
      }

      "non-empty" in {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          // persist test-data
          exec(OutputSchema.table.delete)
          exec(OutputSchema.table ++= outputs)

          // exec
          val hashes = outputs.map(output => (output.txHash, output.blockHash))
          val actual = exec(OutputQueries.outputsFromTxs(hashes))

          // expected query result
          val expected =
            outputs.map { entity =>
              OutputFromTxQR(
                txHash = entity.txHash,
                outputOrder = entity.outputOrder,
                outputType = entity.outputType,
                hint = entity.hint,
                key = entity.key,
                amount = entity.amount,
                address = entity.address,
                grouplessAddress = entity.grouplessAddress,
                tokens = entity.tokens,
                lockTime = entity.lockTime,
                message = entity.message,
                spentFinalized = entity.spentFinalized,
                fixedOutput = entity.fixedOutput
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
        // table is empty
        exec(OutputSchema.table.length.result) is 0

        forAll(outputEntityGen) { output =>
          // exec
          val actual =
            exec(OutputQueries.getOutputsQuery(output.txHash, output.blockHash))

          // query output size is 0
          actual.size is 0
        }
      }
    }

    "non-empty" in {
      forAll(Gen.listOf(outputEntityGen)) { outputs =>
        // no-need to clear the table for each iteration.
        exec(OutputSchema.table ++= outputs)

        // exec for each output
        outputs foreach { output =>
          val actual =
            exec(OutputQueries.getOutputsQuery(output.txHash, output.blockHash))

          // expected query result
          val expected: OutputQR =
            OutputQR(
              outputType = output.outputType,
              hint = output.hint,
              key = output.key,
              amount = output.amount,
              address = output.address,
              grouplessAddress = output.grouplessAddress,
              tokens = output.tokens,
              lockTime = output.lockTime,
              message = output.message,
              spentFinalized = output.spentFinalized,
              fixedOutput = output.fixedOutput
            )

          actual.toList should contain only expected
        }
      }
    }
  }

  "getMainChainOutputs" should {
    "all OutputEntities" when {
      "order is ascending" in {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          exec(OutputSchema.table.delete)
          exec(OutputSchema.table ++= outputs)

          val expected = outputs.filter(_.mainChain).sortBy(_.timestamp)

          // Ascending order
          locally {
            val actual = exec(OutputQueries.getMainChainOutputs(true))
            actual should contain inOrderElementsOf expected
          }

          // Descending order
          locally {
            val expectedReversed = expected.reverse
            val actual           = exec(OutputQueries.getMainChainOutputs(false))
            actual should contain inOrderElementsOf expectedReversed
          }
        }
      }
    }
  }

  "getBalanceAction" should {
    "return None" when {
      "address does not exist" in {
        val address = addressGen.sample getOrElse fail("Failed to sample address")
        exec(
          getBalanceUntilLockTime(address, TimeStamp.now(), TimeStamp.now())
        ) is ((
          None,
          None
        ))
      }
    }
  }

  "index 'outputs_tx_hash_block_hash_idx'" should {
    "get used" when {
      "accessing column tx_hash" ignore {
        forAll(Gen.listOf(outputEntityGen)) { outputs =>
          exec(OutputSchema.table.delete)
          exec(OutputSchema.table ++= outputs)

          outputs foreach { output =>
            val query =
              sql"""
                   SELECT *
                   FROM outputs
                   where tx_hash = ${output.txHash}
                   """

            val explain = exec(query.explain()).mkString("\n")

            explain should include("outputs_tx_hash_block_hash_idx")
          }
        }
      }
    }
  }
}
