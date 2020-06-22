package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.api.model.BlockEntry

trait BlockDepsSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class BlockDeps(tag: Tag) extends Table[(BlockEntry.Hash, BlockEntry.Hash)](tag, "block_deps") {
    def hash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("hash")
    def dep: Rep[BlockEntry.Hash]  = column[BlockEntry.Hash]("dep")

    def hashIdx: Index = index("hash_idx", hash)

    def * : ProvenShape[(BlockEntry.Hash, BlockEntry.Hash)] = (hash, dep)
  }

  val blockDepsTable: TableQuery[BlockDeps] = TableQuery[BlockDeps]
}
