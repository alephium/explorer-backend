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

import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

import akka.util.ByteString
import slick.jdbc._
import slick.jdbc.PostgresProfile._
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.api.model.Val
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model._
import org.alephium.serde._
import org.alephium.util.{TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object CustomJdbcTypes {

  private def buildHashTypes[H: ClassTag](from: Hash => H, to: H => Hash): JdbcType[H] =
    MappedJdbcType.base[H, Array[Byte]](
      to(_).bytes.toArray,
      raw => from(Hash.unsafe(ByteString.fromArrayUnsafe(raw)))
    )

  implicit val hashType: JdbcType[Hash] = buildHashTypes(identity, identity)

  implicit val blockEntryHashType: JdbcType[BlockHash] =
    MappedJdbcType.base[BlockHash, Array[Byte]](
      _.bytes.toArray,
      raw => BlockHash.unsafe(ByteString.fromArrayUnsafe(raw))
    )

  implicit val transactionIdType: JdbcType[TransactionId] =
    buildHashTypes(
      TransactionId.unsafe(_),
      _.value
    )

  implicit val tokenIdType: JdbcType[TokenId] =
    buildHashTypes(
      TokenId.unsafe(_),
      _.value
    )

  implicit val contractIdType: JdbcType[ContractId] =
    buildHashTypes(
      ContractId.unsafe(_),
      _.value
    )

  implicit val groupIndexType: JdbcType[GroupIndex] = MappedJdbcType.base[GroupIndex, Int](
    _.value,
    int => new GroupIndex(int)
  )

  implicit val heightType: JdbcType[Height] = MappedJdbcType.base[Height, Int](
    _.value,
    int => Height.unsafe(int)
  )

  implicit val addressType: JdbcType[Address] = MappedJdbcType.base[Address, String](
    _.toBase58,
    string =>
      Address.fromBase58(string) match {
        case Right(address) => address
        case Left(error) =>
          throw new Exception(s"Unable to decode address from $string: $error")
      }
  )

  implicit val grouplessAddressType: JdbcType[GrouplessAddress] =
    MappedJdbcType.base[GrouplessAddress, String](
      _.toBase58,
      string =>
        ApiAddress.fromBase58(string) match {
          case Right(ApiAddress(lockupScript: ApiAddress.HalfDecodedLockupScript)) =>
            GrouplessAddress(lockupScript)
          case Right(_) =>
            throw new Exception(s"Expecting a groupless address, but got something else: ${string}")
          case Left(error) => throw new Exception(s"Unable to decode address: ${error}")
        }
    )

  implicit val addressContractType: JdbcType[Address.Contract] =
    MappedJdbcType.base[Address.Contract, String](
      _.toBase58,
      string =>
        Address.fromBase58(string) match {
          case Right(address: Address.Contract) => address
          case Right(_: Address.Asset) =>
            throw new Exception(s"Expect contract address, but was asset address: $string")
          case Left(error) =>
            throw new Exception(s"Unable to decode address from $string: $error")
        }
    )

  implicit val timestampType: JdbcType[TimeStamp] = MappedJdbcType.base[TimeStamp, Long](
    _.millis,
    long => TimeStamp.unsafe(long)
  )

  implicit val u256Type: JdbcType[U256] = MappedJdbcType.base[U256, BigDecimal](
    u256 => BigDecimal(u256.v),
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

  implicit val seqByteStringType: JdbcType[ArraySeq[ByteString]] =
    MappedJdbcType.base[ArraySeq[ByteString], Array[Byte]](
      byteStrings => serialize(byteStrings).toArray,
      bytes =>
        deserialize[ArraySeq[ByteString]](ByteString.fromArrayUnsafe(bytes)) match {
          case Left(error)  => throw error
          case Right(value) => value
        }
    )

  implicit val tokensType: JdbcType[ArraySeq[Token]] =
    MappedJdbcType.base[ArraySeq[Token], Array[Byte]](
      tokens => serialize(tokens).toArray,
      bytes =>
        deserialize[ArraySeq[Token]](ByteString.fromArrayUnsafe(bytes)) match {
          case Left(error)  => throw error
          case Right(value) => value
        }
    )

  implicit val seqBlockHashType: JdbcType[ArraySeq[BlockHash]] =
    MappedJdbcType.base[ArraySeq[BlockHash], Array[Byte]](
      hashes => serialize(hashes).toArray,
      bytes =>
        deserialize[ArraySeq[BlockHash]](ByteString.fromArrayUnsafe(bytes)) match {
          case Left(error)  => throw error
          case Right(value) => value
        }
    )

  implicit val valsType: JdbcType[ArraySeq[Val]] =
    MappedJdbcType.base[ArraySeq[Val], Array[Byte]](
      vals => writeBinary(vals),
      bytes => readBinary[ArraySeq[Val]](bytes)
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

  implicit val interfaceIdType: JdbcType[InterfaceIdEntity] =
    MappedJdbcType.base[InterfaceIdEntity, String](
      _.id,
      InterfaceIdEntity.from
    )

  implicit val appStateKey: JdbcType[AppStateKey[_]] =
    MappedJdbcType.base[AppStateKey[_], String](
      state => state.key,
      key =>
        AppState
          .keys()
          .find(_.key == key)
          .getOrElse(throw new Exception(s"Invalid ${classOf[AppStateKey[_]].getSimpleName}: $key"))
    )

  implicit val ghostUnclesType: JdbcType[ArraySeq[GhostUncle]] =
    MappedJdbcType.base[ArraySeq[GhostUncle], Array[Byte]](
      uncles => writeBinary(uncles),
      bytes => readBinary[ArraySeq[GhostUncle]](bytes)
    )
}
