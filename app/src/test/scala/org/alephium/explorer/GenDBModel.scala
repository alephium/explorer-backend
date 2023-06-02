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
package org.alephium.explorer

import org.scalacheck.{Arbitrary, Gen}

import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi.valGen
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.Generators._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

/** Test-data generators for types in package [[org.alephium.explorer.persistence.model]]  */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object GenDBModel {

  /** Generates and [[org.alephium.explorer.persistence.model.InputEntity]] for the given
    * [[org.alephium.explorer.persistence.model.OutputEntity]] generator */
  def genInputOutput(
      outputGen: Gen[OutputEntity] = Generators.outputEntityGen): Gen[(InputEntity, OutputEntity)] =
    for {
      output <- outputGen
      input  <- Generators.inputEntityGen(output)
    } yield (input, output)

  def genTransactionPerAddressEntity(
      addressGen: Gen[Address]     = addressGen,
      timestampGen: Gen[TimeStamp] = timestampGen,
      mainChain: Gen[Boolean]      = Arbitrary.arbitrary[Boolean]): Gen[TransactionPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockEntryHashGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- mainChain
      coinbase  <- Arbitrary.arbitrary[Boolean]
    } yield
      TransactionPerAddressEntity(
        address   = address,
        hash      = hash,
        blockHash = blockHash,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain,
        coinbase  = coinbase
      )

  def transactionPerTokenEntityGen(
      blockHash: Gen[BlockHash] = blockEntryHashGen): Gen[TransactionPerTokenEntity] =
    for {
      hash      <- transactionHashGen
      blockHash <- blockHash
      token     <- tokenIdGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
    } yield
      TransactionPerTokenEntity(
        hash      = hash,
        blockHash = blockHash,
        token     = token,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain
      )

  def tokenTxPerAddressEntityGen(
      blockHash: Gen[BlockHash] = blockEntryHashGen): Gen[TokenTxPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockHash
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
      token     <- tokenIdGen
    } yield
      TokenTxPerAddressEntity(
        address   = address,
        hash      = hash,
        blockHash = blockHash,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain,
        token     = token
      )

  def eventEntityGen: Gen[EventEntity] =
    for {
      blockHash       <- blockEntryHashGen
      hash            <- transactionHashGen
      contractAddress <- addressGen
      inputAddress    <- Gen.option(addressGen)
      timestamp       <- timestampGen
      eventIndex      <- Gen.posNum[Int]
      fields          <- Gen.listOf(valGen)

    } yield
      EventEntity.from(
        blockHash,
        hash,
        contractAddress,
        inputAddress,
        timestamp,
        eventIndex,
        fields,
        0
      )

  def tokenOutputEntityGen(
      addressGen: Gen[Address]          = addressGen,
      transactionId: Gen[TransactionId] = transactionHashGen,
      blockHash: Gen[BlockHash]         = blockEntryHashGen,
      timestampGen: Gen[TimeStamp]      = timestampGen,
      mainChain: Gen[Boolean]           = Arbitrary.arbitrary[Boolean]): Gen[TokenOutputEntity] =
    for {
      blockHash      <- blockHash
      txHash         <- transactionId
      timestamp      <- timestampGen
      outputType     <- Gen.choose(0, 1)
      hint           <- Gen.posNum[Int]
      key            <- hashGen
      token          <- tokenIdGen
      amount         <- amountGen
      address        <- addressGen
      mainChain      <- mainChain
      lockTime       <- Gen.option(timestampGen)
      message        <- Gen.option(bytesGen)
      outputOrder    <- Gen.posNum[Int]
      txOrder        <- Gen.posNum[Int]
      spentFinalized <- Gen.option(transactionId)
      spentTimestamp <- Gen.option(timestampGen)
    } yield
      TokenOutputEntity(
        blockHash,
        txHash,
        timestamp,
        outputType, //0 Asset, 1 Contract
        hint,
        key,
        token,
        amount,
        address,
        mainChain,
        lockTime,
        message,
        outputOrder,
        txOrder,
        spentFinalized,
        spentTimestamp
      )

  /** Generates BlockEntity and it's dependant Entities that also maintain the block's mainChain value */
  def blockAndItsMainChainEntitiesGen()
    : Gen[(BlockEntity, TransactionPerTokenEntity, TokenTxPerAddressEntity, TokenOutputEntity)] =
    for {
      chainFrom         <- groupIndexGen
      chainTo           <- groupIndexGen
      entity            <- blockEntityWithParentGen(chainFrom, chainTo, None)
      txnPerToken       <- transactionPerTokenEntityGen(blockHash = entity.hash)
      tokenTxPerAddress <- tokenTxPerAddressEntityGen(blockHash = entity.hash)
      tokenOutput       <- tokenOutputEntityGen(blockHash = entity.hash)
    } yield (entity, txnPerToken, tokenTxPerAddress, tokenOutput)
}
