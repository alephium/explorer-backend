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
import org.alephium.explorer.persistence.DBRunner_V2._
import org.alephium.explorer.persistence.TestDBRunner
import org.alephium.explorer.persistence.queries.BlockDepQueries._
import org.alephium.explorer.persistence.schema.BlockDepsSchema
import org.alephium.explorer.util.TestUtils._

//TODO - Update Generators to an object instead of trait
class BlockDepQueriesSpec extends AlephiumSpec with ScalaFutures with Generators {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "insert and ignore block_deps" in {
    using(TestDBRunner(BlockDepsSchema.table)) { implicit runner =>
      forAll(Gen.listOf(blockDepUpdatedGen)) { deps =>
        //clean existing rows
        runAction(BlockDepsSchema.table.delete).futureValue

        val original = deps.map(_._1)
        val ignored  = deps.map(_._2)

        runAction(insertBlockDeps(original)).futureValue is original.size
        runAction(BlockDepsSchema.table.result).futureValue is original

        //Ignore the same data with do nothing order
        runAction(insertBlockDeps(ignored)).futureValue is 0
        //it should contain original rows
        runAction(BlockDepsSchema.table.result).futureValue should contain allElementsOf original
      }
    }
  }
}
