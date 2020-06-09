package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash

trait BlockDepsSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class BlockDeps(tag: Tag) extends Table[(Hash, Hash)](tag, "block_deps") {
    def hash: Rep[Hash] = column[Hash]("hash")
    def dep: Rep[Hash]  = column[Hash]("dep")

    def hashIdx: Index = index("hash_idx", hash)

    def * : ProvenShape[(Hash, Hash)] = (hash, dep)
  }

  val blockDeps: TableQuery[BlockDeps] = TableQuery[BlockDeps]
}
