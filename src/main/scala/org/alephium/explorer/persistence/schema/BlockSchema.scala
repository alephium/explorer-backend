package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.Block

trait BlockSchema {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Blocks(tag: Tag) extends Table[Block](tag, "blocks") {
    def hash: Rep[String]    = column[String]("hash", O.PrimaryKey)
    def timestamp: Rep[Long] = column[Long]("timestamp")
    def chainFrom: Rep[Int]  = column[Int]("chain_from")
    def chainTo: Rep[Int]    = column[Int]("chain_to")
    def height: Rep[Int]     = column[Int]("height")

    def * : ProvenShape[Block] =
      (hash, timestamp, chainFrom, chainTo, height) <> ((Block.apply _).tupled, Block.unapply)
  }

  val blocks: TableQuery[Blocks] = TableQuery[Blocks]
}
