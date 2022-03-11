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

package org.alephium.explorer.persistence.schema

import java.math.BigInteger

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.util.TimeStamp

object BlockHeaderSchema extends Schema with CustomTypes {
  private val tableName = "block_headers"

  class BlockHeaders(tag: Tag) extends Table[BlockHeader](tag, tableName) {
    def hash: Rep[BlockEntry.Hash] =
      column[BlockEntry.Hash]("hash", O.PrimaryKey, O.SqlType("bytea"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("timestamp")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def height: Rep[Height]        = column[Height]("height")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def nonce: Rep[ByteString]     = column[ByteString]("nonce")
    def version: Rep[Byte]         = column[Byte]("version")
    def depStateHash: Rep[Hash]    = column[Hash]("dep_state_hash")
    def txsHash: Rep[Hash]         = column[Hash]("txs_hash")
    def txsCount: Rep[Int]         = column[Int]("txs_count")
    def target: Rep[ByteString]    = column[ByteString]("target")
    def hashrate: Rep[BigInteger] =
      column[BigInteger]("hashrate", O.SqlType("DECIMAL(80,0)")) //TODO How much decimal we need? this one is the same as for U256
    def parent: Rep[Option[BlockEntry.Hash]] = column[Option[BlockEntry.Hash]]("parent")

    def timestampIdx: Index = index("blocks_timestamp_idx", timestamp)
    def heightIdx: Index    = index("blocks_height_idx", height)

    def * : ProvenShape[BlockHeader] =
      (hash,
       timestamp,
       chainFrom,
       chainTo,
       height,
       mainChain,
       nonce,
       version,
       depStateHash,
       txsHash,
       txsCount,
       target,
       hashrate,
       parent)
        .<>((BlockHeader.apply _).tupled, BlockHeader.unapply)
  }

  /**
    * Indexes all columns of this table so that `INDEX ONLY SCAN`
    * is enough for queries to return results.
    *
    * @see PR <a href="https://github.com/alephium/explorer-backend/pull/112">#112</a>.
    */
  private def fullIndexSQL(): SqlAction[Int, NoStream, Effect] =
    sqlu"""
      create unique index if not exists block_headers_full_index
          on #${tableName} (main_chain asc, timestamp desc, hash asc, chain_from asc, chain_to asc, height asc);
      """

  /**
    * Builds partial index for column `main_chain` when `true` for performant
    * [[org.alephium.explorer.persistence.dao.BlockDao.listMainChain]] queries.
    *
    * @see PR <a href="https://github.com/alephium/explorer-backend/pull/116">#116</a> for benchmarks
    */
  private def mainChainIndexSQL(): SqlAction[Int, NoStream, Effect] =
    mainChainIndex(tableName)

  /**
    * Joins all indexes created via raw SQL
    */
  def createBlockHeadersIndexesSQL(): DBIO[Unit] =
    DBIO.seq(fullIndexSQL(), mainChainIndexSQL())

  val blockHeadersTable: TableQuery[BlockHeaders] = TableQuery[BlockHeaders]
}
