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

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{BlockHash, TokenId, TransactionId}
import org.alephium.serde._
import org.alephium.util.{TimeStamp, U256}

object CustomGetResult {

  /**
    * GetResult types
    */
  implicit val blockEntryHashGetResult: GetResult[BlockHash] =
    (result: PositionedResult) => BlockHash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  implicit val txHashGetResult: GetResult[TransactionId] =
    (result: PositionedResult) =>
      TransactionId.unsafe(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val tokenIdGetResult: GetResult[TokenId] =
    (result: PositionedResult) =>
      TokenId.unsafe(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val optionTxHashGetResult: GetResult[Option[TransactionId]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => TransactionId.unsafe(new Hash(ByteString.fromArrayUnsafe(bytes))))

  implicit val optionBlockEntryHashGetResult: GetResult[Option[BlockHash]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map(bytes => BlockHash.unsafe(ByteString.fromArrayUnsafe(bytes)))

  implicit val timestampGetResult: GetResult[TimeStamp] =
    (result: PositionedResult) => TimeStamp.unsafe(result.nextLong())

  implicit val optionTimestampGetResult: GetResult[Option[TimeStamp]] =
    (result: PositionedResult) => result.nextLongOption().map(TimeStamp.unsafe)

  implicit val groupIndexGetResult: GetResult[GroupIndex] =
    (result: PositionedResult) => GroupIndex.unsafe(result.nextInt())

  implicit val heightGetResult: GetResult[Height] =
    (result: PositionedResult) => Height.unsafe(result.nextInt())

  implicit val heightOptionGetResult: GetResult[Option[Height]] =
    (result: PositionedResult) => result.nextIntOption().map(Height.unsafe)

  implicit val timeStampHeightOptionGetResult: GetResult[Option[(TimeStamp, Height)]] =
    (result: PositionedResult) =>
      for {
        timestamp <- optionTimestampGetResult(result)
        height    <- heightOptionGetResult(result)
      } yield (timestamp, height)

  implicit val bigIntegerGetResult: GetResult[BigInteger] =
    (result: PositionedResult) => result.nextBigDecimal().toBigInt.bigInteger

  implicit val byteStringGetResult: GetResult[ByteString] =
    (result: PositionedResult) => ByteString.fromArrayUnsafe(result.nextBytes())

  implicit val optionByteStringGetResult: GetResult[Option[ByteString]] =
    (result: PositionedResult) =>
      result.nextBytesOption().map(bytes => ByteString.fromArrayUnsafe(bytes))

  implicit val optionTokensGetResult: GetResult[Option[ArraySeq[Token]]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map { bytes =>
          deserialize[ArraySeq[Token]](ByteString.fromArrayUnsafe(bytes)) match {
            case Left(error)  => throw error
            case Right(value) => value
          }
      }

  implicit val hashGetResult: GetResult[Hash] =
    (result: PositionedResult) => Hash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  implicit val addressGetResult: GetResult[Address] =
    (result: PositionedResult) => Address.unsafe(result.nextString())

  implicit val optionAddressGetResult: GetResult[Option[Address]] =
    (result: PositionedResult) => result.nextStringOption().map(string => Address.unsafe(string))

  implicit val u256GetResult: GetResult[U256] =
    (result: PositionedResult) => {
      U256.unsafe(result.nextBigDecimal().toBigInt.bigInteger)
    }

  implicit val optionU256GetResult: GetResult[Option[U256]] =
    (result: PositionedResult) => {
      result.nextBigDecimalOption().map(bigDecimal => U256.unsafe(bigDecimal.toBigInt.bigInteger))
    }

  val outputGetResult: GetResult[OutputEntity] =
    (result: PositionedResult) =>
      OutputEntity(
        blockHash      = result.<<,
        txHash         = result.<<,
        timestamp      = result.<<,
        outputType     = result.<<,
        hint           = result.<<,
        key            = result.<<,
        amount         = result.<<,
        address        = result.<<,
        tokens         = result.<<?,
        mainChain      = result.<<,
        lockTime       = result.<<?,
        message        = result.<<?,
        outputOrder    = result.<<,
        txOrder        = result.<<,
        spentFinalized = result.<<?
    )

  val inputGetResult: GetResult[InputEntity] =
    (result: PositionedResult) =>
      InputEntity(
        blockHash        = result.<<,
        txHash           = result.<<,
        timestamp        = result.<<,
        hint             = result.<<,
        outputRefKey     = result.<<,
        unlockScript     = result.<<?,
        mainChain        = result.<<,
        inputOrder       = result.<<,
        txOrder          = result.<<,
        outputRefTxHash  = result.<<?,
        outputRefAddress = result.<<?,
        outputRefAmount  = result.<<?,
        outputRefTokens  = result.<<?
    )

  implicit val outputTypeGetResult: GetResult[OutputEntity.OutputType] =
    (result: PositionedResult) => OutputEntity.OutputType.unsafe(result.nextInt())

  /**
    * GetResult type for BlockEntryLite
    *
    * @note The order in which the query returns the column values matters.
    *       For example: Getting (`.<<`) `chainTo` before `chainFrom` when
    *       `chainFrom` is before `chainTo` in the query result would compile
    *       but would result in incorrect data.
    */
  val blockEntryListGetResult: GetResult[BlockEntryLite] =
    (result: PositionedResult) =>
      BlockEntryLite(hash      = result.<<,
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

  val unconfirmedTransactionGetResult: GetResult[UnconfirmedTxEntity] =
    (result: PositionedResult) =>
      UnconfirmedTxEntity(
        hash      = result.<<,
        chainFrom = result.<<,
        chainTo   = result.<<,
        gasAmount = result.<<,
        gasPrice  = result.<<,
        lastSeen  = result.<<
    )

  val uinputGetResult: GetResult[UInputEntity] =
    (result: PositionedResult) =>
      UInputEntity(
        txHash       = result.<<,
        hint         = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        address      = result.<<?,
        uinputOrder  = result.<<
    )

  val uoutputGetResult: GetResult[UOutputEntity] =
    (result: PositionedResult) =>
      UOutputEntity(
        txHash       = result.<<,
        hint         = result.<<,
        key          = result.<<,
        amount       = result.<<,
        address      = result.<<,
        tokens       = result.<<?,
        lockTime     = result.<<?,
        message      = result.<<?,
        uoutputOrder = result.<<
    )

  val tokenSupplyGetResult: GetResult[TokenSupplyEntity] =
    (result: PositionedResult) =>
      TokenSupplyEntity(
        timestamp   = result.<<,
        total       = result.<<,
        circulating = result.<<,
        reserved    = result.<<,
        locked      = result.<<
    )

  implicit val chainFromToAndMainChain: GetResult[(GroupIndex, GroupIndex, Boolean)] =
    (result: PositionedResult) => {
      val chainFrom = groupIndexGetResult(result)
      val chainTo   = groupIndexGetResult(result)
      val mainChain = result.nextBoolean()
      (chainFrom, chainTo, mainChain)
    }
}
