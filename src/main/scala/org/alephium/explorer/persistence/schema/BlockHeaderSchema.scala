package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.GroupIndex
import org.alephium.explorer.persistence.model.BlockHeader

trait BlockHeaderSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class BlockHeaders(tag: Tag) extends Table[BlockHeader](tag, "block_headers") {
    def hash: Rep[Hash]            = column[Hash]("hash", O.PrimaryKey)
    def timestamp: Rep[Long]       = column[Long]("timestamp")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def height: Rep[Int]           = column[Int]("height")

    def blocksTimestampIdx: Index = index("blocks_timestamp_idx", timestamp)
    def blocksHeigthIdx: Index    = index("blocks_height_idx", height)

    def * : ProvenShape[BlockHeader] =
      (hash, timestamp, chainFrom, chainTo, height) <> ((BlockHeader.apply _).tupled, BlockHeader.unapply)
  }

  val blockHeaders: TableQuery[BlockHeaders] = TableQuery[BlockHeaders]
}
