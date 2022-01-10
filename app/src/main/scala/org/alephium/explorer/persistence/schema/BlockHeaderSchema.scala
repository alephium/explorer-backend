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

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.model.BlockHeader

trait BlockHeaderSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  //Uppercase names because H2 is case-sensitive when executing raw SQL queries
  class BlockHeaders(tag: Tag) extends Table[BlockHeader](tag, "BLOCK_HEADERS") {
    def hash: Rep[BlockEntry.Hash] =
      column[BlockEntry.Hash]("HASH", O.PrimaryKey, O.SqlType("BYTEA"))
    def timestamp: Rep[Long]       = column[Long]("TIMESTAMP")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("CHAIN_FROM")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("CHAIN_TO")
    def height: Rep[Height]        = column[Height]("HEIGHT")
    def mainChain: Rep[Boolean]    = column[Boolean]("MAIN_CHAIN")

    def timestampIdx: Index = index("blocks_timestamp_idx", timestamp)
    def heightIdx: Index    = index("blocks_height_idx", height)

    def * : ProvenShape[BlockHeader] =
      (hash, timestamp, chainFrom, chainTo, height, mainChain)
        .<>((BlockHeader.apply _).tupled, BlockHeader.unapply)
  }

  /**
    * Builds index for all columns of this table so that
    * index scan is enough for the query to return results.
    *
    * @see PR <a href="https://github.com/alephium/explorer-backend/pull/112">#112</a>.
    */
  def createBlockHeadersFullIndexSQL(): SqlAction[Int, NoStream, Effect] = {
    sqlu"""
      create unique index if not exists block_headers_full_index
          on block_headers (main_chain asc, timestamp desc, hash asc, chain_from asc, chain_to asc, height asc);
      """
  }

  val blockHeadersTable: TableQuery[BlockHeaders] = TableQuery[BlockHeaders]
}
