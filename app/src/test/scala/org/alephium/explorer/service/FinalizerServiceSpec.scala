// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.{BlockHeader, InputEntity, LatestBlock}
import org.alephium.explorer.persistence.model.AppState.LastFinalizedInputTime
import org.alephium.explorer.persistence.queries.{AppStateQueries, BlockQueries, InputQueries}
import org.alephium.explorer.persistence.schema.LatestBlockSchema
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.{Duration, TimeStamp}

class FinalizerServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  "getStartEndTime - return nothing if there's no input" in new Fixture {
    exec(FinalizerService.getStartEndTime()) is None
  }

  "getStartEndTime - return nothing if there's only 1 input" in new Fixture {

    val input1 = input(TimeStamp.now())
    exec(InputQueries.insertInputs(Seq(input1)))

    exec(FinalizerService.getStartEndTime()) is None
  }

  "getStartEndTime - return nothing if all inputs are after finalization time" in new Fixture {

    val input1 = input(
      firstFinalizationTime.plusHoursUnsafe(1)
    )
    val input2 = input(
      firstFinalizationTime.plusHoursUnsafe(2)
    )

    exec(InputQueries.insertInputs(Seq(input1, input2)))
    exec(FinalizerService.getStartEndTime()) is None
  }

  "getStartEndTime - return correct finalization time" in new Fixture {

    val input1 = input(
      firstFinalizationTime.minusUnsafe(Duration.ofHoursUnsafe(10))
    )
    val blockHeader2 = block(firstFinalizationTime.minusUnsafe(Duration.ofHoursUnsafe(1)))

    exec(BlockQueries.insertBlockHeaders(Seq(blockHeader2)))
    exec(InputQueries.insertInputs(Seq(input1)))
    exec(LatestBlockSchema.table += latestBlock(blockHeader2))

    val Some((start1, end1)) = exec(FinalizerService.getStartEndTime())

    start1 is input1.timestamp
    end1 is blockHeader2.timestamp

    val input3 = input(TimeStamp.now())

    exec(InputQueries.insertInputs(Seq(input3)))

    val Some((start2, end2)) = exec(FinalizerService.getStartEndTime())

    start2 is input1.timestamp

    firstFinalizationTime.isBefore(end2)
    end2.isBefore(FinalizerService.finalizationTime)

    exec(AppStateQueries.insertOrUpdate(LastFinalizedInputTime(blockHeader2.timestamp)))

    val Some((start3, _)) = exec(FinalizerService.getStartEndTime())

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
