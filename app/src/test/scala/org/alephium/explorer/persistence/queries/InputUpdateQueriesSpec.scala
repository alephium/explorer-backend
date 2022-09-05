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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.Generators._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

class InputUpdateQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  "Input Update" should {
    "update inputs when address is already set" in {
      forAll(outputEntityGen, inputEntityGen()) {
        case (output, input) =>
          run(for {
            _ <- OutputSchema.table += output
            _ <- InputSchema.table +=
              input.copy(outputRefKey = output.key, outputRefAddress = Some(output.address))
          } yield ()).futureValue

          val inputBeforeUpdate =
            run(InputSchema.table.filter(_.outputRefKey === output.key).result.head).futureValue

          inputBeforeUpdate.outputRefAddress is Some(output.address)
          inputBeforeUpdate.outputRefAmount is None

          run(InputUpdateQueries.updateInputs()).futureValue

          val updatedInput =
            run(InputSchema.table.filter(_.outputRefKey === output.key).result.head).futureValue

          updatedInput.outputRefAddress is Some(output.address)
          updatedInput.outputRefAmount is Some(output.amount)
      }
    }
  }
}
