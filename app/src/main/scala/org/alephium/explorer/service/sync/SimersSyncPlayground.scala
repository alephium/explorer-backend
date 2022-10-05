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


import com.typesafe.scalalogging.StrictLogging
import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.{GroupIndex, Height}
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service.BlockFlowClient
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

/**
  * My Sync test-bed.
  */
object SimersSyncPlayground extends StrictLogging {

  /** Another version of [[init]]. Returns a boolean, ignores any [[Future]] failures */
  def initIgnoreError()(implicit ec: ExecutionContext,
                        dc: DatabaseConfig[PostgresProfile],
                        client: BlockFlowClient,
                        groupSetting: GroupSetting,
                        cache: BlockCache): Future[Boolean] =
    init()
      .map(_ => true)
      .recover {
        error =>
          logger.error("Failed to initialise sync", error)
          false
      }

  /** The init function from `BlockFlowSyncService` */
  def init()(implicit ec: ExecutionContext,
             dc: DatabaseConfig[PostgresProfile],
             client: BlockFlowClient,
             groupSetting: GroupSetting,
             cache: BlockCache): Future[Unit] =
    for {
      heightAndGroups <- run(BlockQueries.maxHeightZipped(groupSetting.groupIndexes))
      bestBlocks <- client.fetchRootBlockAtHeights(remoteRootBlocksToFetch(heightAndGroups))
      result <- insert(bestBlocks)
    } yield result

  /**
    * Give a set of blocks, find which blocks are out of sync and then persist
    * them in a single transaction.
    *
    * TODO: return the number of blocks synced
    * */
  def insert(blocks: ArraySeq[BlockEntity])(implicit ec: ExecutionContext,
                                            dc: DatabaseConfig[PostgresProfile],
                                            client: BlockFlowClient,
                                            groupSetting: GroupSetting,
                                            cache: BlockCache): Future[Unit] =
    if (blocks.isEmpty)
      Future.unit
    else
      for {
        outOfSyncBlocks <- findOutOfSyncBlocks(blocks) //find out-of-sync blocks
        missingBlocks <- executeActions(outOfSyncBlocks) //persist out of sync blocks
        fetchedMissingBlocks <- client.fetchMissingBlocks(missingBlocks) //updateMainChain returns missing blocks
        result <- insert(fetchedMissingBlocks) //persist those missing blocks
      } yield result

  /** Does exactly what [[BlockFlowSyncService.init]] does when calling `syncAt` but this just creates a request object
    * to be dispatching with a single request.
    * */
  def remoteRootBlocksToFetch(heightAndGroups: ArraySeq[(Option[Height], (GroupIndex, GroupIndex))]): ArraySeq[SyncRemoteAction.GetRootBlockAtHeights] =
    heightAndGroups flatMap {
      case (height, (fromGroup, toGroup)) =>
        height match {
          case Some(height) =>
            if (height.value == 0)
              Some(SyncRemoteAction.GetRootBlockAtHeights(fromGroup, toGroup, ArraySeq(Height.unsafe(1))))
            else
              None

          case None =>
            Some(SyncRemoteAction.GetRootBlockAtHeights(fromGroup, toGroup, ArraySeq(Height.unsafe(1), Height.unsafe(2))))
        }
    }

  def findOutOfSyncBlocks(blocks: ArraySeq[BlockEntity])(implicit ec: ExecutionContext,
                                                         dc: DatabaseConfig[PostgresProfile],
                                                         client: BlockFlowClient,
                                                         groupSetting: GroupSetting): Future[ArraySeq[SyncLocalAction]] = {
    logger.info(s"Looking for out-of-sync in ${blocks.size} blocks")
    Future.traverse(blocks)(findOutOfSyncBlocks).map(_.flatten)
  }

  /** This does not mutates the database. It simply traverse the block's tree and returns
    *  - new blocks fetched from the node [[SyncLocalAction.InsertNewBlock]]
    *  - blocks the need `main_chain` to be updated [[SyncLocalAction.UpdateMainChain]]
    *
    * No mutation occurs here, only remote node is read so can be executed in parallel.
    *
    * The returned actions can then be executed in a single transaction to move the database sync state forward.
    * */
  def findOutOfSyncBlocks(block: BlockEntity)(implicit ec: ExecutionContext,
                                              dc: DatabaseConfig[PostgresProfile],
                                              blockFlowClient: BlockFlowClient,
                                              groupSetting: GroupSetting): Future[ArraySeq[SyncLocalAction]] =
    block.parent(groupSetting.groupNum) match {
      case Some(parent) =>
        run(BlockQueries.getChainInfoForBlock(parent)) flatMap {
          case None =>
            for {
              parent <- blockFlowClient.fetchBlock(block.chainFrom, parent)
              actions <- findOutOfSyncBlocks(parent)
            } yield actions :+ SyncLocalAction.InsertNewBlock(parent)

          case Some((chainFrom, chainTo, height, mainChain)) =>
            if (!mainChain)
              if (block.chainFrom == chainFrom && block.chainTo == chainTo)
                Future.successful(ArraySeq(SyncLocalAction.UpdateMainChain(parent, height, block)))
              else
                Future.failed(new Exception(s"Invalid state. Block $parent belongs to different chains. ${block.chainFrom} == $chainFrom && ${block.chainTo} == $chainTo"))
            else
              Future.successful(ArraySeq.empty)
        }

      case None =>
        if (block.height.value != 0)
          logger.error(s"${block.hash} doesn't have a parent")

        Future.successful(ArraySeq.empty)
    }

  /** Given a set of action that mutate the local database state.
    *
    * Execute actions and move the database sync state forward transactionally.
    * */
  def executeActions(actions: ArraySeq[SyncLocalAction])(implicit ec: ExecutionContext,
                                                         dc: DatabaseConfig[PostgresProfile],
                                                         blockFlowClient: BlockFlowClient,
                                                         groupSetting: GroupSetting,
                                                         cache: BlockCache): Future[ArraySeq[SyncRemoteAction.GetMissingBlock]] = {
    val newBlocks =
      actions collect {
        case newBlock: SyncLocalAction.InsertNewBlock => newBlock.block
      }

    logger.info(s"Inserting new blocks: ${newBlocks.size}")
    //TODO: the following 3 mutation should all occur within a single transaction
    BlockDao.insertAll(newBlocks) flatMap { //insert all new blocks in a single transaction
      _ =>
        updateLatestBlock(newBlocks.lastOption) flatMap { //update latest blocks
          _ =>
            executeUpdateMainChain(actions) //run updateMainChain
        }
    }
  }

  def updateLatestBlock(block: Option[BlockEntity])(implicit ec: ExecutionContext,
                                                    dc: DatabaseConfig[PostgresProfile],
                                                    blockFlowClient: BlockFlowClient,
                                                    groupSetting: GroupSetting,
                                                    cache: BlockCache): Future[Unit] =
    block match {
      case Some(value) =>
        BlockDao.updateLatestBlock(value)

      case None =>
        Future.unit
    }

  def executeUpdateMainChain(actions: ArraySeq[SyncLocalAction])(implicit ec: ExecutionContext,
                                                                 dc: DatabaseConfig[PostgresProfile],
                                                                 groupSetting: GroupSetting): Future[ArraySeq[SyncRemoteAction.GetMissingBlock]] = {
    logger.info(s"Executing update main chain on ${actions.size} blocks")
    //TODO: Issue <a href="https://github.com/alephium/explorer-backend/issues/350">#350</a>.
    //
    //TODO: Can the following updateMainChain run in parallel instead of foldFutures?
    //      Need to consult the blockchain oracles.
    //
    foldFutures(actions.map(_.updateInfo())) {
      case (hash, chainFrom, chainTo) =>
        BlockDao
          .updateMainChain(hash, chainFrom, chainTo, groupSetting.groupNum)
          .map(_.map(hash => SyncRemoteAction.GetMissingBlock(hash, chainFrom)))
    }.map(_.flatten)
  }

  /**
    * THE FOLLOWING IS WORK IN PROGRESS: NOT TESTED AND NOT READY FOR REVIEW.
    *
    * The following code is an attempt to resolve some of the issues in
    * <a href="https://github.com/alephium/explorer-backend/issues/343">#343</a>
    * and is work-in-progress.
    */
  //
  //    import org.alephium.explorer.persistence.schema.CustomGetResult._
  //    import org.alephium.explorer.persistence.schema.CustomSetParameter._
  //    import org.alephium.explorer.persistence.DBActionR
  //    import org.alephium.protocol.model.BlockHash
  //    import slick.dbio.DBIOAction
  //    import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
  //
  //    def getBlockHashesToUpdate(tasks: ArraySeq[SyncLocalAction])(implicit ec: ExecutionContext): DBActionR[Vector[BlockHash]] =
  //      if (tasks.isEmpty) {
  //        DBIOAction.successful(Vector.empty)
  //      } else {
  //        val chainsUpdated: Set[(GroupIndex, GroupIndex, Height)] =
  //          tasks.map(_.updateInfo()).to(Set)
  //
  //        val chunksToFalse =
  //          chainsUpdated.map {
  //            case (from, to, height) =>
  //              s"""
  //                 |chain_from = $from AND chain_to = $to AND height = $height
  //                 |""".stripMargin
  //          }.mkString("\nOR\n")
  //
  //        val query = s"SELECT hash FROM block_headers WHERE $chunksToFalse"
  //
  //        val parameters: SetParameter[Unit] =
  //          (_: Unit, params: PositionedParameters) =>
  //            chainsUpdated foreach {
  //              case (from, to, height) =>
  //                params >> from
  //                params >> to
  //                params >> height
  //            }
  //
  //        SQLActionBuilder(
  //          queryParts = query,
  //          unitPConv = parameters
  //        ).as[BlockHash]
  //      }
  //
  //    def updateMainChainQuery(mainChain: Boolean, blocks: ArraySeq[BlockHash]) = {
  //      def whereClause(columnName: String): String =
  //        Array.tabulate(blocks.size)(index => s"columnName = $$$index").mkString("\nOR\n")
  //
  //      val query =
  //        s"""
  //           |BEGIN;
  //           |UPDATE transactions              SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE outputs                   SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE inputs                    SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE block_headers             SET main_chain = $$1 WHERE ${whereClause("hash")};
  //           |UPDATE transaction_per_addresses SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE transaction_per_token     SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE token_tx_per_addresses    SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |UPDATE token_outputs             SET main_chain = $$1 WHERE ${whereClause("block_hash")};
  //           |COMMIT;
  //           |
  //           |""".stripMargin
  //
  //      val parameters: SetParameter[Unit] =
  //        (_: Unit, params: PositionedParameters) => {
  //          params >> mainChain
  //          blocks foreach {
  //            hash =>
  //              params >> hash
  //          }
  //        }
  //
  //      SQLActionBuilder(
  //        queryParts = query,
  //        unitPConv = parameters
  //      ).asUpdate
  //    }
  //
  //    def query(actions: ArraySeq[SyncLocalAction])(implicit ec: ExecutionContext) = {
  //      actions map {
  //        case SyncLocalAction.InsertNewBlock(block) =>
  //          block.chainFrom
  //          block.chainTo
  //          block.height
  //
  //          val parentMainChain = block.updateMainChain(newMainChain = true)
  //
  //          """
  //            |UPDATE SET main_chain = false;
  //            |INSERT parent with main_chain = true
  //            |""".stripMargin
  //
  //        case SyncLocalAction.UpdateMainChain(blockHash, height, child) =>
  //          child.chainFrom
  //          child.chainTo
  //          child.height
  //          """
  //            |UPDATE SET main_chain = false;
  //            |UPDATE SET parent with main_chain = true
  //            |""".stripMargin
  //      }
  //    }

}
