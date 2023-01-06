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

package org.alephium.explorer.api

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import sttp.tapir.EndpointIO.Example

import org.alephium.api.EndpointsExamples
import org.alephium.api.model.Amount
import org.alephium.explorer.api.model._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{BlockHash, TokenId}
import org.alephium.util.{Hex, U256}

/**
  * Contains OpenAPI Examples.
  */
// scalastyle:off magic.number
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object EndpointExamples extends EndpointsExamples {

  private def alph(value: Int): Amount =
    Amount(ALPH.oneAlph.mulUnsafe(U256.unsafe(value)))

  private val blockHash: BlockHash =
    BlockHash
      .from(Hex.unsafe("bdaf9dc514ce7d34b6474b8ca10a3dfb93ba997cb9d5ff1ea724ebe2af48abe5"))
      .get

  private val outputRef: OutputRef =
    OutputRef(hint = 23412, key = hash)

  private val unlockScript: ByteString =
    Hex.unsafe("d1b70d2226308b46da297486adb6b4f1a8c1842cb159ac5ec04f384fe2d6f5da28")

  private val address1: Address =
    Address.unsafe("1AujpupFP4KWeZvqA7itsHY9cLJmx4qTzojVZrg8W9y9n")

  private val address2: Address =
    Address.unsafe("3dFzhhHHzGwAWW7MZkTJWubpkW1iJ5gnUjqgAG8HEdhk")

  private val tokens: ArraySeq[Token] =
    ArraySeq(
      Token(TokenId.hash("token1"), alph(42).value),
      Token(TokenId.hash("token2"), alph(1000).value)
    )

  private val input: Input =
    Input(
      outputRef      = outputRef,
      unlockScript   = Some(unlockScript),
      txHashRef      = Some(txId),
      address        = Some(address1),
      attoAlphAmount = Some(U256.Two),
      tokens         = Some(tokens)
    )

  private val outputAsset: AssetOutput =
    AssetOutput(
      hint           = 1,
      key            = hash,
      attoAlphAmount = U256.Two,
      address        = address1,
      tokens         = Some(tokens),
      lockTime       = Some(ts),
      message        = Some(hash.bytes)
    )

  private val outputContract: Output =
    ContractOutput(
      hint           = 1,
      key            = hash,
      attoAlphAmount = U256.Two,
      address        = address1,
      tokens         = Some(tokens)
    )

  /**
    * Main API objects
    */
  private val blockEntryLite: BlockEntryLite =
    BlockEntryLite(
      hash      = blockHash,
      timestamp = ts,
      chainFrom = GroupIndex.unsafe(1),
      chainTo   = GroupIndex.unsafe(2),
      height    = Height.unsafe(42),
      txNumber  = 1,
      mainChain = true,
      hashRate  = BigInteger.valueOf(100L)
    )

  private val transaction: Transaction =
    Transaction(
      hash      = txId,
      blockHash = blockHash,
      timestamp = ts,
      inputs    = ArraySeq(input),
      outputs   = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.defaultGas.value,
      gasPrice  = org.alephium.protocol.model.defaultGasPrice.value
    )

  private val confirmedTransaction =
    ConfirmedTransaction.from(transaction)

  private val unconfirmedTransaction =
    UnconfirmedTransaction(
      hash      = txId,
      chainFrom = GroupIndex.unsafe(1),
      chainTo   = GroupIndex.unsafe(2),
      inputs    = ArraySeq(input),
      outputs   = ArraySeq(outputAsset, outputContract),
      gasAmount = org.alephium.protocol.model.defaultGas.value,
      gasPrice  = org.alephium.protocol.model.defaultGasPrice.value,
      lastSeen  = ts
    )

  /**
    * Examples
    */
  implicit val blockEntryLiteExample: List[Example[BlockEntryLite]] =
    simpleExample(blockEntryLite)

  implicit val transactionsExample: List[Example[ArraySeq[Transaction]]] =
    simpleExample(ArraySeq(transaction, transaction))

  implicit val listOfBlocksExample: List[Example[ListBlocks]] =
    simpleExample(ListBlocks(2, ArraySeq(blockEntryLite, blockEntryLite)))

  implicit val listTokensExample: List[Example[ArraySeq[TokenId]]] =
    simpleExample(tokens.map(_.id))

  implicit val listAddressesExample: List[Example[ArraySeq[Address]]] =
    simpleExample(ArraySeq(address1, address2))

  implicit val transactionLikeExample: List[Example[TransactionLike]] =
    simpleExample(confirmedTransaction)

  implicit val unconfirmedTransactionsLike: List[Example[ArraySeq[UnconfirmedTransaction]]] =
    simpleExample(ArraySeq(unconfirmedTransaction, unconfirmedTransaction))

  implicit val transactionExample: List[Example[Transaction]] =
    simpleExample(transaction)

}
