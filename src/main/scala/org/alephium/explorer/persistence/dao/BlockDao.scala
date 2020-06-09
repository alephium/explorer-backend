package org.alephium.explorer.persistence.dao

import scala.concurrent.Future

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}

trait BlockDao {
  def get(id: Hash): Future[Option[BlockEntry]]
  def insert(block: BlockEntry): Future[Either[String, BlockEntry]]
  def list(timeInterval: TimeInterval): Future[Seq[BlockEntry]]
  def maxHeight(fromGroup: Int, toGroup: Int): Future[Option[Int]]
}
