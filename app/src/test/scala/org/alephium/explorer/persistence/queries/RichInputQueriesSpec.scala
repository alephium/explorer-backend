// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import org.scalamock.scalatest.MockFactory
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.AppState.LastFinalizedInputTime
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.service.{BlockFlowClient, RichInputBackfill}
import org.alephium.util.U256

class RichInputQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner
    with MockFactory {
  "Rich input ingestion" should {
    "index a sender with no outputs and retain fork-specific amounts" in {
      val output = outputEntityGen.sample.get
      val input = inputEntityGen().sample.get.copy(
        outputRefKey = output.key,
        outputRefAddress = Some(output.address),
        outputRefAmount = Some(U256.One),
        outputRefTokens = Some(ArraySeq(tokenGen.sample.get)),
        mainChain = true,
        conflicted = Some(false)
      )
      val fork = input.copy(
        blockHash = inputEntityGen().sample.get.blockHash,
        outputRefAmount = Some(U256.Two),
        mainChain = false
      )
      exec(TransactionQueries.insertAll(ArraySeq.empty, ArraySeq.empty, ArraySeq(input, fork)))
      exec(InputSchema.table.result).toSet is Set(input, fork)
      val addresses = exec(TransactionPerAddressSchema.table.result)
      addresses.map(_.address).toSet is Set(output.address)
      addresses.map(_.mainChain).toSet is Set(true, false)
      addresses.forall(_.conflicted.contains(false)) is true
      exec(TokenPerAddressSchema.table.length.result) is 2
      exec(OutputSchema.table.length.result) is 0
      exec(TransactionQueries.insertAll(ArraySeq.empty, ArraySeq.empty, ArraySeq(input, fork)))
      exec(InputSchema.table.result).toSet is Set(input, fork)
      exec(TransactionPerAddressSchema.table.length.result) is 2
    }

    "repair only incomplete legacy rows and preserve finalized state and indexes" in {
      val output = outputEntityGen.sample.get
      val old    = inputEntityGen().sample.get.copy(mainChain = true, conflicted = Some(true))
      val rich = old.copy(
        outputRefAddress = Some(output.address),
        outputRefAmount = Some(U256.One),
        outputRefTokens = Some(ArraySeq(tokenGen.sample.get)),
        mainChain = false,
        conflicted = None
      )
      val finalizedOutput =
        output.copy(spentFinalized = Some(old.txHash), spentTimestamp = Some(old.timestamp))
      exec(OutputSchema.table += finalizedOutput)
      val watermark = LastFinalizedInputTime(old.timestamp)
      exec(AppStateQueries.insertOrUpdate(watermark))
      exec(InputSchema.table += old)
      exec(RichInputBackfill.repairInputs(old.blockHash, Vector(rich)).transactionally)
      val expected = rich.copy(mainChain = true, conflicted = Some(true))
      exec(InputSchema.table.result.head) is expected
      val index = exec(TransactionPerAddressSchema.table.result.head)
      index.mainChain is true
      index.conflicted is Some(true)
      exec(
        RichInputBackfill
          .repairInputs(old.blockHash, Vector(rich.copy(outputRefAmount = Some(U256.Two))))
          .transactionally
      )
      exec(InputSchema.table.result.head) is expected
      exec(TransactionPerAddressSchema.table.result.head) is index
      exec(TokenPerAddressSchema.table.length.result) is 1
      exec(OutputSchema.table.result.head) is finalizedOutput
      exec(AppStateQueries.get(LastFinalizedInputTime)) is Some(watermark)
    }

    "resume after a failed block without refetching already committed blocks" in {
      val blocks = Vector
        .fill(2)(blockEntityGen(groupSetting.chainIndexes.head).sample.get)
        .sortBy(_.hash.toHexString)
      val rich = blocks.map { block =>
        val output = outputEntityGen.sample.get
        val input = inputEntityGen().sample.get.copy(
          blockHash = block.hash,
          outputRefAddress = Some(output.address),
          outputRefAmount = Some(U256.One)
        )
        block.copy(
          inputs = ArraySeq(input),
          outputs = ArraySeq.empty,
          transactions = ArraySeq.empty
        )
      }
      rich.foreach { block =>
        exec(
          BlockQueries.insertBlockEntity(
            Vector(block.copy(inputs = block.inputs.map(_.copy(outputRefAmount = None)))),
            groupSetting.groupNum
          )
        )
      }
      implicit val client: BlockFlowClient = mock[BlockFlowClient]
      inSequence {
        (client.fetchBlock _)
          .expects(rich(0).chainFrom, rich(0).hash)
          .returning(Future.successful(rich(0)))
        (client.fetchBlock _)
          .expects(rich(1).chainFrom, rich(1).hash)
          .returning(Future.failed(new IllegalStateException("node unavailable")))
        (client.fetchBlock _)
          .expects(rich(1).chainFrom, rich(1).hash)
          .returning(Future.successful(rich(1)))
      }
      RichInputBackfill.run().failed.futureValue.getMessage is "node unavailable"
      exec(InputSchema.table.filter(_.outputRefAmount.isDefined).length.result) is 1
      RichInputBackfill.run().futureValue is ()
      exec(InputSchema.table.filter(_.outputRefAmount.isDefined).length.result) is 2
    }

    "roll back an incomplete block repair when rich data lacks an input" in {
      val first  = inputEntityGen().sample.get
      val second = inputEntityGen().sample.get.copy(blockHash = first.blockHash)
      val output = outputEntityGen.sample.get
      val rich =
        first.copy(outputRefAddress = Some(output.address), outputRefAmount = Some(U256.One))
      exec(InputSchema.table ++= Vector(first, second))
      execFailed(
        RichInputBackfill.repairInputs(first.blockHash, Vector(rich)).transactionally
      ) is a[IllegalStateException]
      exec(InputSchema.table.filter(_.outputRefAmount.isDefined).length.result) is 0
      exec(TransactionPerAddressSchema.table.length.result) is 0
      exec(
        RichInputBackfill
          .repairInputs(
            first.blockHash,
            Vector(
              rich,
              second.copy(outputRefAddress = Some(output.address), outputRefAmount = Some(U256.Two))
            )
          )
          .transactionally
      )
      exec(InputSchema.table.filter(_.outputRefAmount.isDefined).length.result) is 2
    }
  }
}
