// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

class InputUpdateQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "Input Update" should {
    "update inputs when address is already set" in {
      forAll(outputEntityGen, inputEntityGen()) { case (output, input) =>
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

    "update inputs when address is not set" in {
      forAll(outputEntityGen, inputEntityGen()) { case (output, input) =>
        run(for {
          _ <- OutputSchema.table += output
          _ <- InputSchema.table +=
            input.copy(outputRefKey = output.key)
        } yield ()).futureValue

        run(InputUpdateQueries.updateInputs()).futureValue

        val updatedInput =
          run(InputSchema.table.filter(_.outputRefKey === output.key).result.head).futureValue

        updatedInput.outputRefAddress is Some(output.address)
        updatedInput.outputRefAmount is Some(output.amount)
      }
    }
  }
}
