package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}

import org.alephium.explorer.Hash
import org.alephium.util.Hex

trait CustomTypes extends JdbcProfile {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  implicit val hashType: JdbcType[Hash] = MappedJdbcType.base[Hash, String](
    _.toHexString,
    raw => Hash.unsafe(Hex.unsafe(raw))
  )
}
