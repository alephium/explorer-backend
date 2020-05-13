package org.alephium.explorer.service

import scala.concurrent.Future

import org.alephium.explorer.api.model.BlockEntry

trait BlockService {
  def getBlockById(blockId: String): Future[Either[String, BlockEntry]]
}
