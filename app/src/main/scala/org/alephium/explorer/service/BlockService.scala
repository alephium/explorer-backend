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

package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.dao.BlockDao

trait BlockService {
  def getLiteBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]]
  def getBlockTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]]
  def listBlocks(pagination: Pagination): Future[ListBlocks]
  def listMaxHeights(): Future[Seq[PerChainValue]]
}

object BlockService {
  def apply(blockDAO: BlockDao)(implicit executionContext: ExecutionContext): BlockService =
    new Impl(blockDAO)

  private class Impl(blockDao: BlockDao)(implicit executionContext: ExecutionContext)
      extends BlockService {
    def getLiteBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]] =
      blockDao.getLite(hash)

    def getBlockTransactions(hash: BlockEntry.Hash,
                             pagination: Pagination): Future[Seq[Transaction]] =
      blockDao.getTransactions(hash, pagination)

    def listBlocks(pagination: Pagination): Future[ListBlocks] = {
      blockDao.listMainChainSQL(pagination).map {
        case (blocks, total) =>
          ListBlocks(total, blocks)
      }
    }

    def listMaxHeights(): Future[Seq[PerChainValue]] = {
      blockDao
        .latestBlocks()
        .map(_.map {
          case (chainIndex, block) =>
            PerChainValue(chainIndex.from.value, chainIndex.to.value, block.height.value)
        })
    }
  }
}
