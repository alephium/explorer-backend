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
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.schema.InputSchema

class InputQueriesSpec extends AlephiumSpec with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "insert and ignore inputs" in new Fixture {

    import config.profile.api._

    forAll(Gen.listOf(updatedInputEntityGen())) { existingAndUpdates =>
      //fresh table
      run(inputsTable.delete).futureValue

      val existing = existingAndUpdates.map(_._1) //existing inputs
      val ignored  = existingAndUpdates.map(_._2) //updated inputs

      //insert existing
      run(insertInputs(existing)).futureValue is existing.size
      run(inputsTable.result).futureValue should contain allElementsOf existing

      //insert should ignore existing inputs
      run(insertInputs(ignored)).futureValue is 0
      run(inputsTable.result).futureValue should contain allElementsOf existing
    }
  }

  trait Fixture extends DatabaseFixture with DBRunner with Generators with InputSchema {
    val config: DatabaseConfig[JdbcProfile] = databaseConfig
  }
}
