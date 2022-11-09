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

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, Generators}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.BlockDepQueries._
import org.alephium.explorer.persistence.schema.BlockDepsSchema

class BlockDepQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "insert and ignore block_deps" in {

    forAll(Gen.listOf(Generators.blockDepUpdatedGen)) { deps =>
      //clean existing rows
      run(BlockDepsSchema.table.delete).futureValue

      val original = deps.map(_._1)
      val ignored  = deps.map(_._2)

      run(insertBlockDeps(original)).futureValue is original.size
      run(BlockDepsSchema.table.result).futureValue is original

      //Ignore the same data with do nothing order
      run(insertBlockDeps(ignored)).futureValue is 0
      //it should contain original rows
      run(BlockDepsSchema.table.result).futureValue should contain allElementsOf original
    }
  }

}
