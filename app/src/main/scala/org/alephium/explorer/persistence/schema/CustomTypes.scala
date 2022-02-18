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
import slick.jdbc._

import org.alephium.explorer._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.BlockHeader
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

  implicit lazy val timestampGetResult: GetResult[TimeStamp] =
    (result: PositionedResult) =>
      TimeStamp.unsafe(
        result.nextTimestamp().toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli)

  implicit lazy val optionTimestampGetResult: GetResult[Option[TimeStamp]] =
    (result: PositionedResult) =>
      result.nextTimestampOption().map { timestamp =>
        TimeStamp.unsafe(
          timestamp.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli)
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

  /**
    * GetResult types
    */
  implicit lazy val blockEntryHashGetResult: GetResult[BlockEntry.Hash] =
    (result: PositionedResult) =>
      new BlockEntry.Hash(new BlockHash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit lazy val txHashGetResult: GetResult[Transaction.Hash] =
    (result: PositionedResult) =>
      new Transaction.Hash(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit lazy val optionTxHashGetResult: GetResult[Option[Transaction.Hash]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => new Transaction.Hash(new Hash(ByteString.fromArrayUnsafe(bytes))))

  implicit lazy val optionBlockEntryHashGetResult: GetResult[Option[BlockEntry.Hash]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => new BlockEntry.Hash(new BlockHash(ByteString.fromArrayUnsafe(bytes))))

  implicit lazy val groupIndexGetResult: GetResult[GroupIndex] =
    (result: PositionedResult) => GroupIndex.unsafe(result.nextInt())

  implicit lazy val heightGetResult: GetResult[Height] =
    (result: PositionedResult) => Height.unsafe(result.nextInt())

  implicit lazy val bigIntegerGetResult: GetResult[BigInteger] =
    (result: PositionedResult) => result.nextBigDecimal().toBigInt.bigInteger

  implicit lazy val bytestringGetResult: GetResult[ByteString] =
    (result: PositionedResult) => ByteString.fromArrayUnsafe(result.nextBytes())

  implicit lazy val hashGetResult: GetResult[Hash] =
    (result: PositionedResult) => Hash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  implicit lazy val addressGetResult: GetResult[Address] =
    (result: PositionedResult) => Address.unsafe(result.nextString())

  implicit lazy val u256GetResult: GetResult[U256] =
    (result: PositionedResult) => {
      U256.unsafe(result.nextBigDecimal().toBigInt.bigInteger)
    }

  implicit lazy val optionU256GetResult: GetResult[Option[U256]] =
    (result: PositionedResult) => {
      result.nextBigDecimalOption().map(bigDecimal => U256.unsafe(bigDecimal.toBigInt.bigInteger))
    }

  /**
    * GetResult type for BlockEntry.Lite
    *
    * @note The order in which the query returns the column values matters.
    *       For example: Getting (`.<<`) `chainTo` before `chainFrom` when
    *       `chainFrom` is before `chainTo` in the query result would compile
    *       but would result in incorrect data.
    */
  val blockEntryListGetResult: GetResult[BlockEntry.Lite] =
    (result: PositionedResult) =>
      BlockEntry.Lite(hash      = result.<<,
                      timestamp = result.<<,
                      chainFrom = result.<<,
                      chainTo   = result.<<,
                      height    = result.<<,
                      mainChain = result.<<,
                      hashRate  = result.<<,
                      txNumber  = result.<<)

  val blockHeaderGetResult: GetResult[BlockHeader] =
    (result: PositionedResult) =>
      BlockHeader(
        hash         = result.<<,
        timestamp    = result.<<,
        chainFrom    = result.<<,
        chainTo      = result.<<,
        height       = result.<<,
        mainChain    = result.<<,
        nonce        = result.<<,
        version      = result.<<,
        depStateHash = result.<<,
        txsHash      = result.<<,
        txsCount     = result.<<,
        target       = result.<<,
        hashrate     = result.<<,
        parent       = result.<<?
    )

  /*
   * SetParameters types
   */

  implicit lazy val setAddress: SetParameter[Address] = (v: Address, pp: PositionedParameters) =>
    pp.setString(v.value)

  implicit lazy val setTimeStamp: SetParameter[TimeStamp] =
    (v: TimeStamp, pp: PositionedParameters) =>
      pp.setTimestamp(java.sql.Timestamp.from(java.time.Instant.ofEpochMilli(v.millis)))
}
