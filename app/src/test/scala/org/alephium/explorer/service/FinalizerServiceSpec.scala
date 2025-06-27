// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{BlockHeader, InputEntity, LatestBlock}
import org.alephium.explorer.persistence.model.AppState.LastFinalizedInputTime
import org.alephium.explorer.persistence.queries.{AppStateQueries, BlockQueries, InputQueries}
import org.alephium.explorer.persistence.schema.LatestBlockSchema
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.{Duration, TimeStamp}

class FinalizerServiceSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "getStartEndTime - return nothing if there's no input" in new Fixture {
    run(FinalizerService.getStartEndTime()).futureValue is None
  }

  "getStartEndTime - return nothing if there's only 1 input" in new Fixture {

    val input1 = input(TimeStamp.now())
    run(InputQueries.insertInputs(Seq(input1))).futureValue

    run(FinalizerService.getStartEndTime()).futureValue is None
  }

  "getStartEndTime - return nothing if all inputs are after finalization time" in new Fixture {

    val input1 = input(
      firstFinalizationTime.plusHoursUnsafe(1)
    )
    val input2 = input(
      firstFinalizationTime.plusHoursUnsafe(2)
    )

    run(InputQueries.insertInputs(Seq(input1, input2))).futureValue
    run(FinalizerService.getStartEndTime()).futureValue is None
  }

  "getStartEndTime - return correct finalization time" in new Fixture {

    val input1 = input(
      firstFinalizationTime.minusUnsafe(Duration.ofHoursUnsafe(10))
    )
    val blockHeader2 = block(firstFinalizationTime.minusUnsafe(Duration.ofHoursUnsafe(1)))

    run(BlockQueries.insertBlockHeaders(Seq(blockHeader2))).futureValue
    run(InputQueries.insertInputs(Seq(input1))).futureValue
    run(LatestBlockSchema.table += latestBlock(blockHeader2)).futureValue

    val Some((start1, end1)) = run(FinalizerService.getStartEndTime()).futureValue

    start1 is input1.timestamp
    end1 is blockHeader2.timestamp

    val input3 = input(TimeStamp.now())

    run(InputQueries.insertInputs(Seq(input3))).futureValue

    val Some((start2, end2)) = run(FinalizerService.getStartEndTime()).futureValue

    start2 is input1.timestamp

    firstFinalizationTime.isBefore(end2)
    end2.isBefore(FinalizerService.finalizationTime)

    run(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(blockHeader2.timestamp))).futureValue

    val Some((start3, _)) = run(FinalizerService.getStartEndTime()).futureValue

    start3 is blockHeader2.timestamp.plusMillisUnsafe(1)
  }

  trait Fixture {
    // FinalizerService.finalizationTime is a function based on TimeStamp.now()
    val firstFinalizationTime = FinalizerService.finalizationTime

    def input(timestamp: TimeStamp): InputEntity =
      inputEntityGen().sample.get.copy(timestamp = timestamp, mainChain = true)

    def block(timestamp: TimeStamp): BlockHeader =
      blockHeaderGen.sample.get.copy(
        timestamp = timestamp,
        chainFrom = new GroupIndex(0),
        chainTo = new GroupIndex(0),
        mainChain = true
      )

    def latestBlock(block: BlockHeader): LatestBlock =
      LatestBlock(
        block.hash,
        block.timestamp,
        block.chainFrom,
        block.chainTo,
        block.height,
        block.target,
        block.hashrate
      )
  }
}
