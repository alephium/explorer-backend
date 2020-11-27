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

import io.circe.parser.decode
import io.circe.syntax._
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}

import org.alephium.api.CirceUtils
import org.alephium.explorer.Hash
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

  implicit val hashType: JdbcType[Hash] = buildHashTypes(identity, identity)

  implicit val blockEntryHashType: JdbcType[BlockEntry.Hash] =
    buildHashTypes(
      new BlockEntry.Hash(_),
      _.value
    )

  implicit val blockDepsType: JdbcType[BlockEntry.Deps] =
    MappedJdbcType.base[BlockEntry.Deps, String](
      deps => CirceUtils.print(deps.value.asJson),
      rawJson =>
        decode[BlockEntry.Deps](rawJson)
          .getOrElse(throw new RuntimeException(s"Cannot decode $rawJson for block deps"))
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

  implicit val u256Type: JdbcType[U256] = MappedJdbcType.base[U256, Array[Byte]](
    u256  => u256.toBytes.toArray,
    bytes => U256.unsafe(akka.util.ByteString(bytes))
  )
}
