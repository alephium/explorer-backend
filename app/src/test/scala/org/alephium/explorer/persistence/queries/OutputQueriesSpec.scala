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
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.schema.OutputSchema

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
}
