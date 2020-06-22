package org.alephium.explorer.persistence.schema

import scala.reflect.ClassTag

import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.util.Hex

trait CustomTypes extends JdbcProfile {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private def buildHashTypes[H: ClassTag](from: Hash => H, to: H => Hash): JdbcType[H] =
    MappedJdbcType.base[H, String](
      to(_).toHexString,
      raw => from((Hash.unsafe(Hex.unsafe(raw))))
    )

  implicit val hashType: JdbcType[Hash] = buildHashTypes(identity, identity)

  implicit val blockEntryHashType: JdbcType[BlockEntry.Hash] =
    buildHashTypes(
      new BlockEntry.Hash(_),
      _.value
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
