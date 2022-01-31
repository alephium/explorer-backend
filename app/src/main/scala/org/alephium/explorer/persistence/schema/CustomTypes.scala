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

import java.math.BigInteger

import scala.reflect.ClassTag

import akka.util.ByteString
import slick.basic.DatabaseConfig
import slick.jdbc.{GetResult, JdbcProfile, JdbcType}

import org.alephium.explorer._
import org.alephium.explorer.api.model.{Address, BlockEntry, GroupIndex, Height, Transaction}
import org.alephium.util.{TimeStamp, U256}

trait CustomTypes extends JdbcProfile {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private def buildHashTypes[H: ClassTag](from: Hash => H, to: H => Hash): JdbcType[H] =
    MappedJdbcType.base[H, Array[Byte]](
      to(_).bytes.toArray,
      raw => from(Hash.unsafe(ByteString.fromArrayUnsafe(raw)))
    )

  private def buildBlockHashTypes[H: ClassTag](from: BlockHash => H,
                                               to: H           => BlockHash): JdbcType[H] =
    MappedJdbcType.base[H, Array[Byte]](
      to(_).bytes.toArray,
      raw => from(BlockHash.unsafe(ByteString.fromArrayUnsafe(raw)))
    )

  implicit lazy val hashType: JdbcType[Hash] = buildHashTypes(identity, identity)

  implicit lazy val blockEntryHashType: JdbcType[BlockEntry.Hash] =
    buildBlockHashTypes(
      new BlockEntry.Hash(_),
      _.value
    )

  implicit lazy val transactionHashType: JdbcType[Transaction.Hash] =
    buildHashTypes(
      new Transaction.Hash(_),
      _.value
    )

  implicit lazy val groupIndexType: JdbcType[GroupIndex] = MappedJdbcType.base[GroupIndex, Int](
    _.value,
    int => GroupIndex.unsafe(int)
  )

  implicit lazy val heightType: JdbcType[Height] = MappedJdbcType.base[Height, Int](
    _.value,
    int => Height.unsafe(int)
  )

  implicit lazy val addressType: JdbcType[Address] = MappedJdbcType.base[Address, String](
    _.value,
    string => Address.unsafe(string)
  )


  implicit lazy val timestampGetResult: GetResult[TimeStamp] = GetResult.GetTimestamp.andThen {
    sqlTs =>
      TimeStamp.unsafe(sqlTs.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli)
  }

  implicit lazy val timestampType: JdbcType[TimeStamp] =
    MappedJdbcType.base[TimeStamp, java.time.Instant](
      ts      => java.time.Instant.ofEpochMilli(ts.millis),
      instant => TimeStamp.unsafe(instant.toEpochMilli)
    )
  implicit lazy val u256Type: JdbcType[U256] = MappedJdbcType.base[U256, BigDecimal](
    u256       => BigDecimal(u256.v),
    bigDecimal => U256.unsafe(bigDecimal.toBigInt.bigInteger)
  )

  implicit lazy val bigIntegerType: JdbcType[BigInteger] =
    MappedJdbcType.base[BigInteger, BigDecimal](
      bigInteger => BigDecimal(bigInteger),
      bigDecimal => bigDecimal.toBigInt.bigInteger
    )

  implicit lazy val bytestringType: JdbcType[ByteString] =
    MappedJdbcType.base[ByteString, Array[Byte]](
      _.toArray,
      bytes => ByteString.fromArrayUnsafe(bytes)
    )

  implicit val hashGetResult: GetResult[BlockEntry.Hash] = GetResult[BlockEntry.Hash]
  implicit val groupIndexGetResult: GetResult[GroupIndex] = GetResult[GroupIndex]
  implicit val heightGetResult: GetResult[Height] = GetResult[Height]
  implicit val bigIntegerGetResult: GetResult[BigInteger] = GetResult[BigInteger]

  implicit lazy val blockEntryLiteGetResult :GetResult[BlockEntry.Lite] =  GetResult.createGetTuple7[BlockEntry.Hash, TimeStamp, GroupIndex, GroupIndex, Height, BigInteger, Int].andThen {
    case (hash, timestamp, chainFrom, chainTo, height, hashRate, txNumber) =>
          BlockEntry.Lite(
            hash      = hash,
            timestamp = timestamp,
            chainFrom = chainFrom,
            chainTo   = chainTo,
            height    = height,
            txNumber  = txNumber,
            mainChain = true,
            hashRate  = hashRate
          )
      }

}
