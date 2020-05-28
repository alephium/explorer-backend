package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future}

import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.explorer.persistence.dao.BlockDao

trait BlockService {
  def getBlockById(blockId: String): Future[Either[String, BlockEntry]]
  def listBlocks(timeInterval: TimeInterval): Future[Either[String, Seq[BlockEntry]]]
}

object BlockService {
  def apply(blockDAO: BlockDao)(implicit executionContext: ExecutionContext): BlockService =
    new Impl(blockDAO)

  private class Impl(blockDao: BlockDao)(implicit executionContext: ExecutionContext)
      extends BlockService {
    def getBlockById(blockId: String): Future[Either[String, BlockEntry]] =
      blockDao.get(blockId).map(_.toRight(s"Block with id $blockId not found"))

    def listBlocks(timeInterval: TimeInterval): Future[Either[String, Seq[BlockEntry]]] =
      blockDao.list(timeInterval).map(Right(_))
  }
}
