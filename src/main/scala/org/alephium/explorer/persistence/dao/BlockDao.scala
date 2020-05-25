package org.alephium.explorer.persistence.dao

import scala.concurrent.Future

import org.alephium.explorer.api.model.BlockEntry

trait BlockDao {
  def get(id: String): Future[Option[BlockEntry]]
  def insert(block: BlockEntry): Future[Either[String, BlockEntry]]
}
