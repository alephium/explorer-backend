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

import scala.concurrent.Future

import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.explorer.persistence.dao.BlockDao

trait BlockService {
  def getBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def listBlocks(timeInterval: TimeInterval): Future[Seq[BlockEntry.Lite]]
}

object BlockService {
  def apply(blockDAO: BlockDao): BlockService =
    new Impl(blockDAO)

  private class Impl(blockDao: BlockDao) extends BlockService {
    def getBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      blockDao.get(hash)

    def listBlocks(timeInterval: TimeInterval): Future[Seq[BlockEntry.Lite]] =
      blockDao.listMainChain(timeInterval)
  }
}
