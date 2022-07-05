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

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{AppState, InputEntity}
import org.alephium.explorer.persistence.queries.InputQueries
import org.alephium.explorer.persistence.schema.AppStateSchema
import org.alephium.serde._
import org.alephium.util.{Duration, TimeStamp}

class FinalizerServiceSpec
    extends AlephiumSpec
    with Generators
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
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
    val input2 = input(firstFinalizationTime.minusUnsafe(Duration.ofHoursUnsafe(1)))

    run(InputQueries.insertInputs(Seq(input1, input2))).futureValue

    val Some((start1, end1)) = run(FinalizerService.getStartEndTime()).futureValue

    start1 is input1.timestamp
    end1 is input2.timestamp

    val input3 = input(TimeStamp.now())

    run(InputQueries.insertInputs(Seq(input3))).futureValue

    val Some((start2, end2)) = run(FinalizerService.getStartEndTime()).futureValue

    start2 is input1.timestamp

    firstFinalizationTime.isBefore(end2)
    end2.isBefore(FinalizerService.finalizationTime)

    run(
      AppStateSchema.table.insertOrUpdate(
        AppState("last_finalized_input_time", serialize(input2.timestamp)))).futureValue

    val Some((start3, _)) = run(FinalizerService.getStartEndTime()).futureValue

    start3 is input2.timestamp
  }

  trait Fixture {
    // FinalizerService.finalizationTime is a function based on TimeStamp.now()
    val firstFinalizationTime = FinalizerService.finalizationTime

    def input(timestamp: TimeStamp): InputEntity =
      inputEntityGen().sample.get.copy(timestamp = timestamp, mainChain = true)
  }
}
