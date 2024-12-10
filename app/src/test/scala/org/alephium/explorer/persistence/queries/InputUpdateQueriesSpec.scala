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

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

class InputUpdateQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "Input Update" should {
    "update fixed inputs when address is already set" in {
      forAll(fixedOutputEntityGen, fixedInputEntityGen()) { case (output, input) =>
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

    "update fixed inputs when address is not set" in {
      forAll(fixedOutputEntityGen, fixedInputEntityGen()) { case (output, input) =>
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

    "update contract inputs when two outputs with same key have different value. See issue #584" in {
      forAll(contractOutputEntityGen, contractInputEntityGen(), amountGen, amountGen) {
        case (output, input, amount1, amount2) =>
          val mainChainOutput =
            output.copy(mainChain = true, amount = amount1)
          val nonMainChainOutput =
            output.copy(mainChain = false, amount = amount2, blockHash = blockHashGen.sample.get)

          val mainChainInput = input.copy(mainChain = true, outputRefKey = mainChainOutput.key)
          val nonMainChainInput = input.copy(
            mainChain = false,
            outputRefKey = nonMainChainOutput.key,
            blockHash = blockHashGen.sample.get
          )
          run(for {
            _ <- OutputSchema.table ++= Seq(mainChainOutput, nonMainChainOutput)
            _ <- InputSchema.table ++= Seq(mainChainInput, nonMainChainInput)
          } yield ()).futureValue

          run(InputUpdateQueries.updateInputs()).futureValue

          val updatedInputs =
            run(InputSchema.table.filter(_.outputRefKey === output.key).result).futureValue

          updatedInputs.find(_.mainChain == true).get.outputRefAddress is Some(output.address)
          updatedInputs.find(_.mainChain == true).get.outputRefAmount is Some(amount1)

          updatedInputs.find(_.mainChain == false).get.outputRefAddress is Some(output.address)
          updatedInputs.find(_.mainChain == false).get.outputRefAmount is Some(amount2)
      }
    }

    "update contract 2 input (main_chain, non_main_chain) using same output with same amoun" in {
      forAll(contractOutputEntityGen, contractInputEntityGen()) { case (output, input) =>
        val input1 = input.copy(mainChain = true, outputRefKey = output.key)
        val input2 = input.copy(
          mainChain = false,
          outputRefKey = output.key,
          blockHash = blockHashGen.sample.get
        )
        run(for {
          _ <- OutputSchema.table += output
          _ <- InputSchema.table ++= Seq(input1, input2)
        } yield ()).futureValue

        run(InputUpdateQueries.updateInputs()).futureValue

        val updatedInputs =
          run(InputSchema.table.filter(_.outputRefKey === output.key).result).futureValue

        updatedInputs.foreach(_.outputRefAddress is Some(output.address))
        updatedInputs.foreach(_.outputRefAmount is Some(output.amount))
      }
    }
  }
}
