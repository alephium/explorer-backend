package org.alephium.explorer.service

import scala.concurrent.Future

import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.explorer.persistence.dao.BlockDao

trait BlockService {
  def getBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def listBlocks(timeInterval: TimeInterval): Future[Seq[BlockEntry]]
}

object BlockService {
  def apply(blockDAO: BlockDao): BlockService =
    new Impl(blockDAO)

  private class Impl(blockDao: BlockDao) extends BlockService {
    def getBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      blockDao.get(hash)

    def listBlocks(timeInterval: TimeInterval): Future[Seq[BlockEntry]] =
      blockDao.list(timeInterval)
  }
}
