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
import slick.jdbc._
import slick.jdbc.PostgresProfile._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model.{AppState, AppStateKey, OutputEntity}
import org.alephium.serde._
import org.alephium.util.{AVector, TimeStamp, U256}

object CustomJdbcTypes {

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

  implicit val bigIntegerType: JdbcType[BigInteger] =
    MappedJdbcType.base[BigInteger, BigDecimal](
      bigInteger => BigDecimal(bigInteger),
      bigDecimal => bigDecimal.toBigInt.bigInteger
    )

  implicit val bytestringType: JdbcType[ByteString] =
    MappedJdbcType.base[ByteString, Array[Byte]](
      _.toArray,
      bytes => ByteString.fromArrayUnsafe(bytes)
    )

  implicit val seqByteStringType: JdbcType[AVector[ByteString]] =
    MappedJdbcType.base[AVector[ByteString], Array[Byte]](
      byteStrings => serialize(AVector.unsafe(byteStrings.toArray)).toArray,
      bytes =>
        deserialize[AVector[ByteString]](ByteString.fromArrayUnsafe(bytes)) match {
          case Left(error)  => throw error
          case Right(value) => value
      }
    )

  implicit val tokensType: JdbcType[AVector[Token]] =
    MappedJdbcType.base[AVector[Token], Array[Byte]](
      tokens => serialize(AVector.unsafe(tokens.toArray)).toArray,
      bytes =>
        deserialize[AVector[Token]](ByteString.fromArrayUnsafe(bytes)) match {
          case Left(error)  => throw error
          case Right(value) => value
      }
    )

  implicit val intervalTypeType: JdbcType[IntervalType] =
    MappedJdbcType.base[IntervalType, Int](
      _.value,
      IntervalType.unsafe
    )

  implicit val outputTypeType: JdbcType[OutputEntity.OutputType] =
    MappedJdbcType.base[OutputEntity.OutputType, Int](
      _.value,
      OutputEntity.OutputType.unsafe
    )

  implicit val appStateKey: JdbcType[AppStateKey] =
    MappedJdbcType.base[AppStateKey, String](
      state => state.key,
      key =>
        AppState
          .keys()
          .find(_.key == key)
          .getOrElse(throw new Exception(s"Invalid ${classOf[AppStateKey].getSimpleName}: $key"))
    )
}
