package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.util.Hex

trait CustomTypes extends JdbcProfile {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  implicit val hashType: JdbcType[Hash] = MappedJdbcType.base[Hash, String](
    _.toHexString,
    raw => Hash.unsafe(Hex.unsafe(raw))
  )

  implicit val blockEntryHashType: JdbcType[BlockEntry.Hash] =
    MappedJdbcType.base[BlockEntry.Hash, String](
      _.value.toHexString,
      raw => BlockEntry.Hash.unsafe(raw)
    )

  implicit val groupIndexType: JdbcType[GroupIndex] = MappedJdbcType.base[GroupIndex, Int](
    _.value,
    int => GroupIndex.unsafe(int)
  )

  implicit val heightType: JdbcType[Height] = MappedJdbcType.base[Height, Int](
    _.value,
    int => Height.unsafe(int)
  )
}
