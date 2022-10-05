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

package org.alephium.explorer.service.sync

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.api.model.{GroupIndex, Height}
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.protocol.model.BlockHash

/** A [[SyncAction]] can be either local (database) or remote (node) operation.
  *
  * Local actions describe how the database needs to mutate to move the sync forward.
  *
  * Remote actions describe what data need to be fetch from the node.
  * */
sealed trait SyncAction

/** Local-sync-action describes the mutation the needs to occur
  * in the local database for the sync to progress.
  *
  * @IMPORTANT: These are executed within a single database transaction
  *             which will move the sync forward in a single step.
  * */
sealed trait SyncLocalAction extends SyncAction {
  def chainFrom(): GroupIndex
  def chainTo(): GroupIndex
  def hash(): BlockHash
  def height(): Height

  def updateInfo(): (BlockHash, GroupIndex, GroupIndex) =
    (hash(), chainFrom(), chainTo())

  def chain(): (GroupIndex, GroupIndex) =
    (chainFrom(), chainTo())
}

object SyncLocalAction {
  /**
    * A new block to persist.
    */
  case class InsertNewBlock(block: BlockEntity) extends SyncLocalAction {
    override def hash(): BlockHash = block.hash
    override def chainFrom(): GroupIndex = block.chainFrom
    override def chainTo(): GroupIndex = block.chainTo
    override def height(): Height = block.height
  }

  /**
    * This block needs "updateMainChain" executed
    */
  case class UpdateMainChain(hash: BlockHash,
                             height: Height,
                             child: BlockEntity) extends SyncLocalAction {
    override def chainFrom(): GroupIndex = child.chainFrom
    override def chainTo(): GroupIndex = child.chainTo
  }
}

/**
  * During sync there are cases where we send multiple requests to the
  * same APIs. In the explorer we need these APIs to be a placeholder
  * for the future when we can fetch the same data within a single request.
  *
  * These data-types help with that.
  */
sealed trait SyncRemoteAction extends SyncAction
case object SyncRemoteAction {

  /** Fetch head blocks from the chain at multiple heights */
  case class GetRootBlockAtHeights(chainFrom: GroupIndex,
                                   chainTo: GroupIndex,
                                   heights: ArraySeq[Height]) extends SyncAction

  /** Fetch a missing block */
  case class GetMissingBlock(hash: BlockHash,
                             chainFrom: GroupIndex) extends SyncAction
}
