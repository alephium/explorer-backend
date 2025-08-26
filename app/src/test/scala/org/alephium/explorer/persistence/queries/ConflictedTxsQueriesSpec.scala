// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

//scalastyle:off file.size.limit
package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.cache._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex, TransactionId}
import org.alephium.util.TimeStamp

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class ConflictedTxsQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  "Find conflicted transactions" when {
    "2 main chain input share same output ref key " in new Fixture {
      val input1 = mainChainInput()
      val input2 = mainChainInput(input1.outputRefKey)

      insertInputs(input1, input2)

      val conflicts = exec(
        ConflictedTxsQueries
          .findConflictedTxs(TimeStamp.zero)
      )

      val expected = findConflictedTxsExpected(input1, input2)

      conflicts should contain theSameElementsAs expected
    }

    "3 main chain input share same output ref key " in new Fixture {
      val input1 = mainChainInput()
      val input2 = mainChainInput(input1.outputRefKey)
      val input3 = mainChainInput(input1.outputRefKey)

      insertInputs(input1, input2, input3)

      val conflicts = exec(
        ConflictedTxsQueries
          .findConflictedTxs(TimeStamp.zero)
      )

      val expected = findConflictedTxsExpected(input1, input2, input3)

      conflicts should contain theSameElementsAs expected
    }

    // Could occur when we first sync the two blocks of the two firt inputs,
    // the  algorithm will flag them as conflicted
    // but on next sync, we might receive a third block with the same output ref key
    "3 main chain input share same output ref key, 2 already checked" in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 = mainChainInput(input1.outputRefKey).copy(conflicted = Some(false))
      val input3 = mainChainInput(input1.outputRefKey)

      insertInputs(input1, input2, input3)

      val conflicts = exec(
        ConflictedTxsQueries
          .findConflictedTxs(TimeStamp.zero)
      )

      val expected = findConflictedTxsExpected(input1, input2, input3)

      conflicts should contain theSameElementsAs expected
    }
  }

  "Not find conflicted transactions" when {
    "1 main chain input and 1 not " in new Fixture {
      val input1 = mainChainInput()
      val input2 = mainChainInput(input1.outputRefKey).copy(mainChain = false)

      insertInputs(input1, input2)

      val conflicts = exec(
        ConflictedTxsQueries
          .findConflictedTxs(TimeStamp.zero)
      )

      conflicts is ArraySeq.empty
    }
  }

  "Find already conflicted transactions, but one isn't in main chain anymore" when {
    "2 inputs " in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 =
        mainChainInput(input1.outputRefKey).copy(mainChain = false, conflicted = Some(false))

      insertInputs(input1, input2)

      val updates = exec(
        ConflictedTxsQueries
          .findReorgedConflictedTxs(TimeStamp.zero)
      )

      val expected = findUpdatedConflictedTxsExpected(input1, input2)

      updates should contain theSameElementsAs expected
    }

    "3 inputs, 2 in main chain, 1 not " in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 = mainChainInput(input1.outputRefKey).copy(conflicted = Some(false))
      val input3 =
        mainChainInput(input1.outputRefKey).copy(mainChain = false, conflicted = Some(false))

      insertInputs(input1, input2, input3)

      val updates = exec(
        ConflictedTxsQueries
          .findReorgedConflictedTxs(TimeStamp.zero)
      )

      val expected = findUpdatedConflictedTxsExpected(input1, input2, input3)

      updates should contain theSameElementsAs expected
    }

    "3 inputs, 1 in main chain, 2 not " in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 =
        mainChainInput(input1.outputRefKey).copy(mainChain = false, conflicted = Some(false))
      val input3 =
        mainChainInput(input1.outputRefKey).copy(mainChain = false, conflicted = Some(false))

      insertInputs(input1, input2, input3)

      val updates = exec(
        ConflictedTxsQueries
          .findReorgedConflictedTxs(TimeStamp.zero)
      )

      val expected = findUpdatedConflictedTxsExpected(input1, input2, input3)

      updates should contain theSameElementsAs expected
    }

    "1 input comes from a conflicted transaction" in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 = mainChainInput().copy(outputRefTxHash = Some(input1.txHash))

      insertInputs(input1, input2)

      val updates = exec(
        ConflictedTxsQueries
          .findTxsUsingConflictedTxs(TimeStamp.zero)
      )

      val expected = findConflictedTxsExpected(input2)

      updates should contain theSameElementsAs expected
    }

    "2 inputs comes from a conflicted transaction" in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 = mainChainInput().copy(outputRefTxHash = Some(input1.txHash))
      val input3 = mainChainInput().copy(outputRefTxHash = Some(input1.txHash))

      insertInputs(input1, input2, input3)

      val updates = exec(
        ConflictedTxsQueries
          .findTxsUsingConflictedTxs(TimeStamp.zero)
      )

      val expected = findConflictedTxsExpected(input2, input3)

      updates should contain theSameElementsAs expected
    }
  }

  "Not find conflicted inputs" when {
    "2 inputs are in main_chain" in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 =
        mainChainInput(input1.outputRefKey).copy(conflicted = Some(false))

      insertInputs(input1, input2)

      val updates = exec(
        ConflictedTxsQueries
          .findReorgedConflictedTxs(TimeStamp.zero)
      )

      updates is ArraySeq.empty
    }

    "1 input isn't confirmed yet" in new Fixture {
      val input1 = mainChainInput().copy(conflicted = Some(true))
      val input2 =
        mainChainInput(input1.outputRefKey).copy(mainChain = false, conflicted = None)

      insertInputs(input1, input2)

      val updates = exec(
        ConflictedTxsQueries
          .findReorgedConflictedTxs(TimeStamp.zero)
      )

      updates is ArraySeq.empty
    }
  }

  trait Fixture {
    implicit val blockCache: BlockCache             = TestBlockCache()
    implicit val transactionCache: TransactionCache = TestTransactionCache()

    val groupIndex = GroupIndex.Zero
    val chainIndex = ChainIndex(groupIndex, groupIndex)

    def insertInputs(inputs: InputEntity*) =
      exec(InputSchema.table ++= ArraySeq(inputs: _*))

    def mainChainInput(): InputEntity = inputEntityGen().sample.get.copy(mainChain = true)
    def mainChainInput(outputRefKey: Hash): InputEntity =
      mainChainInput().copy(outputRefKey = outputRefKey)

    def findConflictedTxsExpected(
        inputs: InputEntity*
    ): ArraySeq[(TransactionId, BlockHash, Option[Boolean])] =
      inputs.map { input =>
        (input.txHash, input.blockHash, input.conflicted)
      }

    def findUpdatedConflictedTxsExpected(
        inputs: InputEntity*
    ): ArraySeq[(TransactionId, BlockHash)] =
      inputs.map { input =>
        (input.txHash, input.blockHash)
      }

  }
}
