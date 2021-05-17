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

import scala.reflect.ClassTag

import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}

import org.alephium.explorer._
import org.alephium.explorer.api.model.{Address, BlockEntry, GroupIndex, Height, Transaction}
import org.alephium.util.{Hex, TimeStamp, U256}

trait CustomTypes extends JdbcProfile {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private def buildHashTypes[H: ClassTag](from: Hash => H, to: H => Hash): JdbcType[H] =
    MappedJdbcType.base[H, String](
      to(_).toHexString,
      raw => from((Hash.unsafe(Hex.unsafe(raw))))
    )

  private def buildBlockHashTypes[H: ClassTag](from: BlockHash => H,
                                               to: H           => BlockHash): JdbcType[H] =
    MappedJdbcType.base[H, String](
      to(_).toHexString,
      raw => from((BlockHash.unsafe(Hex.unsafe(raw))))
    )

  implicit val hashType: JdbcType[Hash] = buildHashTypes(identity, identity)

  implicit val blockEntryHashType: JdbcType[BlockEntry.Hash] =
    buildBlockHashTypes(
      new BlockEntry.Hash(_),
      _.value
    )

  implicit val transactionHashType: JdbcType[Transaction.Hash] =
    buildHashTypes(
      new Transaction.Hash(_),
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

  implicit val addressType: JdbcType[Address] = MappedJdbcType.base[Address, String](
    _.value,
    string => Address.unsafe(string)
  )

  implicit val timestampType: JdbcType[TimeStamp] = MappedJdbcType.base[TimeStamp, Long](
    _.millis,
    long => TimeStamp.unsafe(long)
  )

  implicit val u256Type: JdbcType[U256] = MappedJdbcType.base[U256, BigDecimal](
    u256       => BigDecimal(u256.v),
    bigDecimal => U256.unsafe(bigDecimal.toBigInt.bigInteger)
  )
}
