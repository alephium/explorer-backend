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
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.model.BlockHeader

trait BlockHeaderSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class BlockHeaders(tag: Tag) extends Table[BlockHeader](tag, "block_headers") {
    def hash: Rep[BlockEntry.Hash] =
      column[BlockEntry.Hash]("hash", O.PrimaryKey, O.SqlType("BYTEA"))
    def timestamp: Rep[Long]       = column[Long]("timestamp")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def height: Rep[Height]        = column[Height]("height")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def nonce: Rep[ByteString]     = column[ByteString]("nonce")
    def version: Rep[Byte]         = column[Byte]("version")
    def depStateHash: Rep[Hash]    = column[Hash]("dep_state_hash")
    def txsHash: Rep[Hash]         = column[Hash]("txs_hash")
    def target: Rep[ByteString]    = column[ByteString]("target")
    def hashrate: Rep[BigInteger] =
      column[BigInteger]("hashrate", O.SqlType("DECIMAL(80,0)")) //TODO How much decimal we need? this one is the same as for U256

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
       target,
       hashrate)
        .<>((BlockHeader.apply _).tupled, BlockHeader.unapply)
  }

  val blockHeadersTable: TableQuery[BlockHeaders] = TableQuery[BlockHeaders]
}
