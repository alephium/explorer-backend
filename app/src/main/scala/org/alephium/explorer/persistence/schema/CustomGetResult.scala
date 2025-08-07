// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.api.model.Val
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHeader => _, _}
import org.alephium.serde._
import org.alephium.util.{TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object CustomGetResult {

  /** GetResult types
    */
  implicit val blockEntryHashGetResult: GetResult[BlockHash] =
    (result: PositionedResult) => BlockHash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  implicit val txHashGetResult: GetResult[TransactionId] =
    (result: PositionedResult) =>
      TransactionId.unsafe(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val tokenIdGetResult: GetResult[TokenId] =
    (result: PositionedResult) =>
      TokenId.unsafe(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

  implicit val contractIdGetResult: GetResult[ContractId] =
    (result: PositionedResult) =>
      ContractId.unsafe(new Hash(ByteString.fromArrayUnsafe(result.nextBytes())))

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
    (result: PositionedResult) => new GroupIndex(result.nextInt())

  implicit val groupIndexOptionGetResult: GetResult[Option[GroupIndex]] = {
    (result: PositionedResult) => result.nextIntOption().map(new GroupIndex(_))
  }

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

  implicit val arrayByteGetResult: GetResult[Array[Byte]] =
    (result: PositionedResult) => result.nextBytes()

  implicit val byteStringGetResult: GetResult[ByteString] =
    (result: PositionedResult) => ByteString.fromArrayUnsafe(result.nextBytes())

  implicit val optionByteStringGetResult: GetResult[Option[ByteString]] =
    (result: PositionedResult) =>
      result.nextBytesOption().map(bytes => ByteString.fromArrayUnsafe(bytes))

  implicit val optionByteStringsGetResult: GetResult[Option[ArraySeq[ByteString]]] =
    (result: PositionedResult) =>
      result
        .nextBytesOption()
        .map { bytes =>
          deserialize[ArraySeq[ByteString]](ByteString.fromArrayUnsafe(bytes)) match {
            case Left(error)  => throw error
            case Right(value) => value
          }
        }

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

  implicit val blockHashesGetResult: GetResult[ArraySeq[BlockHash]] =
    (result: PositionedResult) =>
      deserialize[ArraySeq[BlockHash]](ByteString.fromArrayUnsafe(result.nextBytes())) match {
        case Left(error)  => throw error
        case Right(value) => value
      }

  implicit val valsGetResult: GetResult[ArraySeq[Val]] =
    (result: PositionedResult) => readBinary[ArraySeq[Val]](result.nextBytes())

  implicit val hashGetResult: GetResult[Hash] =
    (result: PositionedResult) => Hash.unsafe(ByteString.fromArrayUnsafe(result.nextBytes()))

  private def getAddressFromString(string: String): Address =
    Address.fromBase58(string) match {
      case Right(address) => address
      case Left(error)    => throw new Exception(s"Unable to decode address: ${error}")
    }

  private def getGrouplessAddressFromString(string: String): GrouplessAddress =
    ApiAddress.fromBase58(string) match {
      case Right(ApiAddress(lockupScript: ApiAddress.HalfDecodedLockupScript)) =>
        GrouplessAddress(lockupScript)
      case Right(_) =>
        throw new Exception(s"Expecting a groupless address, but got something else: ${string}")
      case Left(error) => throw new Exception(s"Unable to decode address: ${error}")
    }

  implicit val addressGetResult: GetResult[Address] =
    (result: PositionedResult) => getAddressFromString(result.nextString())

  implicit val grouplessAddressGetResult: GetResult[GrouplessAddress] =
    (result: PositionedResult) => getGrouplessAddressFromString(result.nextString())

  implicit val apiAddressGetResult: GetResult[ApiAddress] =
    (result: PositionedResult) =>
      ApiAddress.fromBase58(result.nextString()) match {
        case Right(address) => address
        case Left(error)    => throw new Exception(s"Unable to decode address: ${error}")
      }

  val addressContractGetResult: GetResult[Address.Contract] =
    (result: PositionedResult) => {
      val string = result.nextString()
      Address.fromBase58(string) match {
        case Right(address: Address.Contract) => address
        case Right(_: Address.Asset) =>
          throw new Exception(s"Expect contract address, but was asset address: $string")
        case Left(error) =>
          throw new Exception(s"Unable to decode address from $string: $error")
      }
    }

  implicit val optionAddressGetResult: GetResult[Option[Address]] =
    (result: PositionedResult) =>
      result
        .nextStringOption()
        .map(getAddressFromString)

  implicit val optionGrouplessAddressGetResult: GetResult[Option[GrouplessAddress]] =
    (result: PositionedResult) =>
      result
        .nextStringOption()
        .map(getGrouplessAddressFromString)

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
        blockHash = result.<<,
        txHash = result.<<,
        timestamp = result.<<,
        outputType = result.<<,
        hint = result.<<,
        key = result.<<,
        amount = result.<<,
        address = result.<<,
        grouplessAddress = result.<<?,
        tokens = result.<<?,
        mainChain = result.<<,
        lockTime = result.<<?,
        message = result.<<?,
        outputOrder = result.<<,
        txOrder = result.<<,
        coinbase = result.<<,
        spentFinalized = result.<<?,
        spentTimestamp = result.<<?,
        fixedOutput = result.<<
      )

  val inputGetResult: GetResult[InputEntity] =
    (result: PositionedResult) =>
      InputEntity(
        blockHash = result.<<,
        txHash = result.<<,
        timestamp = result.<<,
        hint = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        mainChain = result.<<,
        inputOrder = result.<<,
        txOrder = result.<<,
        outputRefTxHash = result.<<?,
        outputRefAddress = result.<<?,
        outputRefGrouplessAddress = result.<<?,
        outputRefAmount = result.<<?,
        outputRefTokens = result.<<?,
        contractInput = result.<<
      )

  implicit val addressTotalTransactionGetResult: GetResult[AddressTotalTransactionsEntity] =
    (result: PositionedResult) =>
      AddressTotalTransactionsEntity(
        address = result.<<,
        total = result.<<,
        lastUpdate = result.<<
      )

  implicit val outputTypeGetResult: GetResult[OutputEntity.OutputType] =
    (result: PositionedResult) => OutputEntity.OutputType.unsafe(result.nextInt())

  implicit val interfaceIdGetResult: GetResult[InterfaceIdEntity] =
    (result: PositionedResult) => InterfaceIdEntity.from(result.nextString())

  implicit val optionInterfaceIdGetResult: GetResult[Option[InterfaceIdEntity]] =
    (result: PositionedResult) => result.nextStringOption().map(InterfaceIdEntity.from)

  implicit val optionGhostUnclesGetResult: GetResult[Option[ArraySeq[GhostUncle]]] =
    (result: PositionedResult) =>
      result.nextBytesOption().map(bytes => readBinary[ArraySeq[GhostUncle]](bytes))

  /** GetResult type for BlockEntryLite
    *
    * @note
    *   The order in which the query returns the column values matters. For example: Getting (`.<<`)
    *   `chainTo` before `chainFrom` when `chainFrom` is before `chainTo` in the query result would
    *   compile but would result in incorrect data.
    */
  val blockEntryListGetResult: GetResult[BlockEntryLite] =
    (result: PositionedResult) =>
      BlockEntryLite(
        hash = result.<<,
        timestamp = result.<<,
        chainFrom = result.<<,
        chainTo = result.<<,
        height = result.<<,
        mainChain = result.<<,
        hashRate = result.<<,
        txNumber = result.<<
      )

  val blockHeaderGetResult: GetResult[BlockHeader] =
    (result: PositionedResult) =>
      BlockHeader(
        hash = result.<<,
        timestamp = result.<<,
        chainFrom = result.<<,
        chainTo = result.<<,
        height = result.<<,
        mainChain = result.<<,
        nonce = result.<<,
        version = result.<<,
        depStateHash = result.<<,
        txsHash = result.<<,
        txsCount = result.<<,
        target = result.<<,
        hashrate = result.<<,
        parent = result.<<?,
        deps = result.<<,
        ghostUncles = result.<<?
      )

  val transactionEntityGetResult: GetResult[TransactionEntity] =
    (result: PositionedResult) =>
      TransactionEntity(
        hash = result.<<,
        blockHash = result.<<,
        timestamp = result.<<,
        chainFrom = result.<<,
        chainTo = result.<<,
        version = result.<<,
        networkId = result.<<,
        scriptOpt = result.<<?,
        gasAmount = result.<<,
        gasPrice = result.<<,
        order = result.<<,
        mainChain = result.<<,
        scriptExecutionOk = result.<<,
        inputSignatures = result.<<?,
        scriptSignatures = result.<<?,
        coinbase = result.<<
      )

  val mempoolTransactionGetResult: GetResult[MempoolTransactionEntity] =
    (result: PositionedResult) =>
      MempoolTransactionEntity(
        hash = result.<<,
        chainFrom = result.<<,
        chainTo = result.<<,
        gasAmount = result.<<,
        gasPrice = result.<<,
        lastSeen = result.<<
      )

  val uinputGetResult: GetResult[UInputEntity] =
    (result: PositionedResult) =>
      UInputEntity(
        txHash = result.<<,
        hint = result.<<,
        outputRefKey = result.<<,
        unlockScript = result.<<?,
        address = result.<<?,
        grouplessAddress = result.<<?,
        uinputOrder = result.<<
      )

  val uoutputGetResult: GetResult[UOutputEntity] =
    (result: PositionedResult) =>
      UOutputEntity(
        txHash = result.<<,
        hint = result.<<,
        key = result.<<,
        amount = result.<<,
        address = result.<<,
        grouplessAddress = result.<<?,
        tokens = result.<<?,
        lockTime = result.<<?,
        message = result.<<?,
        uoutputOrder = result.<<
      )

  val tokenSupplyGetResult: GetResult[TokenSupplyEntity] =
    (result: PositionedResult) =>
      TokenSupplyEntity(
        timestamp = result.<<,
        total = result.<<,
        circulating = result.<<,
        reserved = result.<<,
        locked = result.<<
      )

  implicit val chainFromToAndMainChain: GetResult[(GroupIndex, GroupIndex, Boolean)] =
    (result: PositionedResult) => {
      val chainFrom = groupIndexGetResult(result)
      val chainTo   = groupIndexGetResult(result)
      val mainChain = result.nextBoolean()
      (chainFrom, chainTo, mainChain)
    }

  val eventGetResult: GetResult[EventEntity] =
    (result: PositionedResult) =>
      EventEntity(
        blockHash = result.<<,
        txHash = result.<<,
        contractAddress = result.<<,
        inputAddress = result.<<?,
        timestamp = result.<<,
        eventIndex = result.<<,
        fields = result.<<,
        eventOrder = result.<<
      )

  val fungibleTokenMetadataGetResult: GetResult[FungibleTokenMetadata] =
    (result: PositionedResult) =>
      FungibleTokenMetadata(
        id = result.<<,
        symbol = result.<<,
        name = result.<<,
        decimals = result.<<
      )

  val nftMetadataGetResult: GetResult[NFTMetadata] =
    (result: PositionedResult) =>
      NFTMetadata(
        id = result.<<,
        tokenUri = result.<<,
        collectionId = result.<<,
        nftIndex = result.<<
      )

  val nftCollectionMetadataGetResult: GetResult[NFTCollectionMetadata] =
    (result: PositionedResult) =>
      NFTCollectionMetadata(
        address = result.<<(addressContractGetResult),
        collectionUri = result.<<
      )

  val tokenInfoGetResult: GetResult[TokenInfoEntity] =
    (result: PositionedResult) =>
      TokenInfoEntity(
        token = result.<<,
        lastUsed = result.<<,
        category = result.<<?,
        interfaceId = result.<<?
      )

  val contractEntityGetResult: GetResult[ContractEntity] =
    (result: PositionedResult) =>
      ContractEntity(
        contract = result.<<,
        parent = result.<<?,
        stdInterfaceIdGuessed = result.<<?,
        creationBlockHash = result.<<,
        creationTxHash = result.<<,
        creationTimestamp = result.<<,
        creationEventOrder = result.<<,
        destructionBlockHash = result.<<?,
        destructionTxHash = result.<<?,
        destructionTimestamp = result.<<?,
        destructionEventOrder = result.<<?,
        category = result.<<?,
        interfaceId = result.<<?
      )

  val latestBlockGetResult: GetResult[LatestBlock] =
    (result: PositionedResult) =>
      LatestBlock(
        hash = result.<<,
        timestamp = result.<<,
        chainFrom = result.<<,
        chainTo = result.<<,
        height = result.<<,
        target = result.<<,
        hashrate = result.<<
      )

  implicit val migrationVersionGetResult: GetResult[AppState.MigrationVersion] =
    (result: PositionedResult) =>
      AppState.MigrationVersion(ByteString.fromArrayUnsafe(result.nextBytes())) match {
        case Left(error)  => throw error
        case Right(value) => value
      }

  implicit val lastFinalizedInputTimeGetResult: GetResult[AppState.LastFinalizedInputTime] =
    (result: PositionedResult) =>
      AppState.LastFinalizedInputTime(ByteString.fromArrayUnsafe(result.nextBytes())) match {
        case Left(error)  => throw error
        case Right(value) => value
      }

  implicit val lastHolderUpdateGetResult: GetResult[AppState.LastHoldersUpdate] =
    (result: PositionedResult) =>
      AppState.LastHoldersUpdate(ByteString.fromArrayUnsafe(result.nextBytes())) match {
        case Left(error)  => throw error
        case Right(value) => value
      }

  implicit val finalizedTxCountGetResult: GetResult[AppState.FinalizedTxCount] =
    (result: PositionedResult) =>
      AppState.FinalizedTxCount(ByteString.fromArrayUnsafe(result.nextBytes())) match {
        case Left(error)  => throw error
        case Right(value) => value
      }
}
