// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import java.math.BigInteger

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.Height
import org.alephium.explorer.persistence.model.LatestBlock
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

object LatestBlockSchema extends Schema[LatestBlock]("latest_blocks") {

  class LatestBlocks(tag: Tag) extends Table[LatestBlock](tag, name) {
    def hash: Rep[BlockHash]       = column[BlockHash]("hash", O.SqlType("bytea"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def height: Rep[Height]        = column[Height]("height")
    def target: Rep[ByteString]    = column[ByteString]("target")
    def hashrate: Rep[BigInteger] =
      column[BigInteger](
        "hashrate",
        O.SqlType("DECIMAL(80,0)")
      ) // TODO How much decimal we need? this one is the same as for U256

    def pk: PrimaryKey = primaryKey("latest_block_pk", (chainFrom, chainTo))

    def * : ProvenShape[LatestBlock] =
      (hash, timestamp, chainFrom, chainTo, height, target, hashrate)
        .<>((LatestBlock.apply _).tupled, LatestBlock.unapply)
  }

  val table: TableQuery[LatestBlocks] = TableQuery[LatestBlocks]
}
