package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

trait BlockDepsSchema {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class BlockDeps(tag: Tag) extends Table[(String, String)](tag, "block_deps") {
    def hash: Rep[String] = column[String]("hash")
    def dep: Rep[String]  = column[String]("dep")

    def hashIdx: Index = index("hash_idx", hash)

    def * : ProvenShape[(String, String)] = (hash, dep)
  }

  val blockDeps: TableQuery[BlockDeps] = TableQuery[BlockDeps]
}
