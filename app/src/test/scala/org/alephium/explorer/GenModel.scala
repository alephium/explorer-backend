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

import org.alephium.explorer.persistence.model._

/** Test-data generators for types in package [[org.alephium.explorer.persistence.model]]  */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object GenModel extends Generators {

  /** Generates and [[org.alephium.explorer.persistence.model.InputEntity]] for the given
    * [[org.alephium.explorer.persistence.model.OutputEntity]] generator */
  def genInputOutput(
      outputGen: Gen[OutputEntity] = outputEntityGen): Gen[(InputEntity, OutputEntity)] =
    for {
      output <- outputGen
      input  <- inputEntityGen(output)
    } yield (input, output)

  /** Convert input-output to [[org.alephium.explorer.persistence.model.TransactionPerAddressEntity]] */
  def toTransactionPerAddressEntity(input: InputEntity,
                                    output: OutputEntity): TransactionPerAddressEntity =
    TransactionPerAddressEntity(
      hash      = output.txHash,
      address   = output.address,
      blockHash = output.blockHash,
      timestamp = output.timestamp,
      txOrder   = input.txOrder,
      mainChain = output.mainChain
    )

  /** Convert multiple input-outputs to [[org.alephium.explorer.persistence.model.TransactionPerAddressEntity]] */
  def toTransactionPerAddressEntities(
      inputOutputs: Iterable[(InputEntity, OutputEntity)]): Iterable[TransactionPerAddressEntity] =
    inputOutputs map {
      case (input, output) =>
        toTransactionPerAddressEntity(input, output)
    }

  def genTransactionPerAddressEntity(): Gen[TransactionPerAddressEntity] =
    for {
      address   <- addressGen
      hash      <- transactionHashGen
      blockHash <- blockEntryHashGen
      timestamp <- timestampGen
      txOrder   <- Gen.posNum[Int]
      mainChain <- Arbitrary.arbitrary[Boolean]
    } yield
      TransactionPerAddressEntity(
        address   = address,
        hash      = hash,
        blockHash = blockHash,
        timestamp = timestamp,
        txOrder   = txOrder,
        mainChain = mainChain
      )

}
