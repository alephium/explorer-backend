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

import org.alephium.explorer.api.model.{Input, Output, Token}
import org.alephium.protocol.model.{Address, AddressLike, TokenId}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.U256

object UtxoUtil {

  def addressEqual(address: Address, addressLike: AddressLike): Boolean = {
    addressLike.lockupScriptResult match {
      case LockupScript.CompleteLockupScript(_) =>
        addressLike.toBase58 == address.toBase58
      case LockupScript.HalfDecodedP2PK(_) =>
        addressLike.toBase58 == address.toBase58.takeWhile(_ != ':')
    }
  }

  def amountForAddressInInputs(address: AddressLike, inputs: ArraySeq[Input]): Option[U256] =
    sumAmounts(
      inputs
        .filter(_.address.exists(a => addressEqual(a, address)))
        .map(_.attoAlphAmount)
        .collect { case Some(amount) => amount }
    )

  def amountForAddressInOutputs(address: AddressLike, outputs: ArraySeq[Output]): Option[U256] =
    sumAmounts(
      outputs
        .filter(o => addressEqual(o.address, address))
        .map(_.attoAlphAmount)
    )

  def tokenAmountForAddressInInputs(
      address: AddressLike,
      inputs: ArraySeq[Input]
  ): Map[TokenId, Option[U256]] =
    sumTokensById(
      inputs
        .filter(_.address.exists(a => addressEqual(a, address)))
        .flatMap(_.tokens)
        .flatten
    )

  def tokenAmountForAddressInOutputs(
      address: AddressLike,
      outputs: ArraySeq[Output]
  ): Map[TokenId, Option[U256]] =
    sumTokensById(
      outputs
        .filter(o => addressEqual(o.address, address))
        .flatMap(_.tokens)
        .flatten
    )

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

  def deltaTokenAmountForAddress(
      address: AddressLike,
      inputs: ArraySeq[Input],
      outputs: ArraySeq[Output]
  ): Map[TokenId, BigInteger] = {
    val in  = tokenAmountForAddressInInputs(address, inputs)
    val out = tokenAmountForAddressInOutputs(address, outputs)

    (in.keySet ++ out.keySet).foldLeft(Map.empty[TokenId, BigInteger]) { case (acc, token) =>
      val i = in.get(token).flatten.getOrElse(U256.Zero)
      val o = out.get(token).flatten.getOrElse(U256.Zero)

      val delta = o.v.subtract(i.v)
      if (delta == BigInteger.ZERO) {
        acc
      } else {
        acc + (token -> delta)
      }
    }
  }

  def sumAmounts(amounts: Iterable[U256]): Option[U256] =
    amounts.foldLeft(Option(U256.Zero)) { case (acc, amount) => acc.flatMap(_.add(amount)) }

  def sumTokensById(tokens: Iterable[Token]): Map[TokenId, Option[U256]] =
    tokens
      .groupBy(_.id)
      .map { case (id, grouped) =>
        (
          id,
          sumAmounts(grouped.map(_.amount))
        )
      }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def fromAddresses(inputs: ArraySeq[Input]): ArraySeq[Address] = {
    inputs.collect { case input if input.address.isDefined => input.address.get }.distinct
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def fromTokenAddresses(token: TokenId, inputs: ArraySeq[Input]): ArraySeq[Address] = {
    inputs.collect {
      case input if input.address.isDefined && input.tokens.exists(_.exists(_.id == token)) =>
        input.address.get
    }.distinct
  }

  def toAddressesWithoutChangeAddresses(
      outputs: ArraySeq[Output],
      changeAddresses: ArraySeq[Address]
  ): ArraySeq[Address] = {
    outputs.collect {
      case output if !changeAddresses.contains(output.address) => output.address
    }.distinct
  }

  def toTokenAddressesWithoutChangeAddresses(
      token: TokenId,
      outputs: ArraySeq[Output],
      changeAddresses: ArraySeq[Address]
  ): ArraySeq[Address] = {
    outputs.collect {
      case output
          if !changeAddresses
            .contains(output.address) && output.tokens.exists(_.exists(_.id == token)) =>
        output.address
    }.distinct
  }
}
