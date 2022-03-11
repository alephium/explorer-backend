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
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.queries.BlockDepQueries._
import org.alephium.explorer.persistence.schema.BlockDepsSchema

class BlockDepQueriesSpec extends AlephiumSpec with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "insert and ignore block_deps" in new Fixture {

    forAll(Gen.listOf(blockDepUpdatedGen)) { deps =>
      //clean existing rows
      run(BlockDepsSchema.blockDepsTable.delete).futureValue

      val original = deps.map(_._1)
      val ignored  = deps.map(_._2)

      run(insertBlockDeps(original)).futureValue is original.size
      run(BlockDepsSchema.blockDepsTable.result).futureValue is original

      //Ignore the same data with do nothing order
      run(insertBlockDeps(ignored)).futureValue is 0
      //it should contain original rows
      run(BlockDepsSchema.blockDepsTable.result).futureValue should contain allElementsOf original
    }
  }

  trait Fixture extends DatabaseFixture with DBRunner with Generators
}
