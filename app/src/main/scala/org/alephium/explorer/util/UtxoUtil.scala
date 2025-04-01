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

package org.alephium.explorer.util

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.api.model.{Input, Output}
import org.alephium.protocol.model.{Address, AddressLike}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.U256

object UtxoUtil {
  def amountForAddressInInputs(address: AddressLike, inputs: ArraySeq[Input]): Option[U256] = {
    inputs
      .filter(input =>
        input.address.map(addressRef => addressEqual(addressRef, address)).getOrElse(false)
      )
      .map(_.attoAlphAmount)
      .collect { case Some(amount) => amount }
      .foldLeft(Option(U256.Zero)) { case (acc, amount) => acc.flatMap(_.add(amount)) }
  }

  def addressEqual(address: Address, addressLike: AddressLike): Boolean = {
    addressLike.lockupScriptResult match {
      case LockupScript.CompleteLockupScript(_) =>
        addressLike.toBase58 == address.toBase58
      case LockupScript.HalfDecodedP2PK(_) =>
        addressLike.toBase58 == address.toBase58.takeWhile(_ != ':')
    }
  }

  def amountForAddressInOutputs(address: AddressLike, outputs: ArraySeq[Output]): Option[U256] = {
    outputs
      .filter(output => addressEqual(output.address, address))
      .map(_.attoAlphAmount)
      .foldLeft(Option(U256.Zero)) { case (acc, amount) => acc.flatMap(_.add(amount)) }
  }

  def deltaAmountForAddress(
      address: AddressLike,
      inputs: ArraySeq[Input],
      outputs: ArraySeq[Output]
  ): Option[BigInteger] = {
    for {
      in  <- amountForAddressInInputs(address, inputs)
      out <- amountForAddressInOutputs(address, outputs)
    } yield {
      out.v.subtract(in.v)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def fromAddresses(inputs: ArraySeq[Input]): ArraySeq[Address] = {
    inputs.collect { case input if input.address.isDefined => input.address.get }.distinct
  }

  def toAddressesWithoutChangeAddresses(
      outputs: ArraySeq[Output],
      changeAddresses: ArraySeq[Address]
  ): ArraySeq[Address] = {
    outputs.collect {
      case output if !changeAddresses.contains(output.address) => output.address
    }.distinct
  }
}
