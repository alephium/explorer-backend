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

import akka.util.Helpers.Requiring

import org.alephium.explorer
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._

/**
  * Temporary holder for ordering. See TODOs below.
  *
  * TODO 1. During PR, if folks are ok with this ordering then these functions should
  *         be moved to their dedicated companion objects.
  *      2. Implement test-cases for the orderings if the approach is ok with others.
  *
  * Implements ordering used by [[BlockQueries.upsertBlockEntity]] to omit duplicate inputs.
  * */
object BlockEntityUpsertOrdering {
  @inline def compare[A](left: A, right: A)(implicit ordering: Ordering[A]): Int =
    ordering.compare(left, right)

  def compare2[A, B](left: (A, B), right: (A, B))(implicit orderingA: Ordering[A],
                                                  orderingB: Ordering[B]): Int = {
    val compareA = orderingA.compare(left._1, right._1)
    if (compareA == 0) {
      orderingB.compare(left._2, right._2)
    } else {
      compareA
    }
  }

  def compare3[A, B, C](left: (A, B, C), right: (A, B, C))(implicit orderingA: Ordering[A],
                                                           orderingB: Ordering[B],
                                                           orderingC: Ordering[C]): Int = {
    val compareA = orderingA.compare(left._1, right._1)
    if (compareA == 0) {
      val compareB = orderingB.compare(left._2, right._2)
      if (compareB == 0) {
        orderingC.compare(left._3, right._3)
      } else {
        compareB
      }
    } else {
      compareA
    }
  }

  implicit val explorerHashOrdering: Ordering[explorer.Hash] =
    (left: explorer.Hash, right: explorer.Hash) =>
      left.value.toHexString compareTo right.value.toHexString

  implicit val explorerBlockHashOrdering: Ordering[explorer.BlockHash] =
    (left: explorer.BlockHash, right: explorer.BlockHash) =>
      left.toHexString compareTo right.toHexString

  implicit val blockEntryHashOrdering: Ordering[BlockEntry.Hash] =
    (left: BlockEntry.Hash, right: BlockEntry.Hash) =>
      left.value.toHexString compareTo right.value.toHexString

  implicit val transactionHashOrdering: Ordering[Transaction.Hash] =
    (left, right) => explorerHashOrdering.compare(left.value, right.value)

  /**
    * Primary key ordering
    */
  val blockDepOrdering: Ordering[BlockDepEntity] =
    (left, right) => compare2(left.primaryKey(), right.primaryKey())

  val transactionOrdering: Ordering[TransactionEntity] =
    (left, right) => compare2(left.primaryKey(), right.primaryKey())

  val inputEntityOrdering: Ordering[InputEntity] =
    (left, right) => compare3(left.primaryKey(), right.primaryKey())

  val outputEntityOrdering: Ordering[OutputEntity] =
    (left, right) => compare2(left.primaryKey(), right.primaryKey())

  val blockHeaderOrdering: Ordering[BlockHeader] =
    (left, right) => compare(left.primaryKey(), right.primaryKey())
}
