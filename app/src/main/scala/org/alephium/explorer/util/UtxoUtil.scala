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
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

object UtxoUtil {
  def amountForAddressInInputs(address: Address, inputs: ArraySeq[Input]): Option[U256] = {
    inputs
      .filter(_.address == Some(address))
      .map(_.attoAlphAmount)
      .collect { case Some(amount) => amount }
      .foldLeft(Option(U256.Zero)) { case (acc, amount) => acc.flatMap(_.add(amount)) }
  }

  def amountForAddressInOutputs(address: Address, outputs: ArraySeq[Output]): Option[U256] = {
    outputs
      .filter(_.address == address)
      .map(_.attoAlphAmount)
      .foldLeft(Option(U256.Zero)) { case (acc, amount) => acc.flatMap(_.add(amount)) }
  }

  def tokenAmountForAddressInInputs(
      address: Address,
      inputs: ArraySeq[Input]
  ): Map[TokenId, Option[U256]] = {
    inputs
      .filter(_.address == Some(address))
      .flatMap(_.tokens)
      .flatten
      .groupBy(_.id)
      .map { case (id, tokens) =>
        (
          id,
          tokens.foldLeft(Option(U256.Zero)) { case (acc, token) =>
            acc.flatMap(_.add(token.amount))
          }
        )
      }
  }

  def tokenAmountForAddressInOutputs(
      address: Address,
      outputs: ArraySeq[Output]
  ): Map[TokenId, Option[U256]] = {
    outputs
      .filter(_.address == address)
      .flatMap(_.tokens)
      .flatten
      .groupBy(_.id)
      .map { case (id, tokens) =>
        (
          id,
          tokens.foldLeft(Option(U256.Zero)) { case (acc, token) =>
            acc.flatMap(_.add(token.amount))
          }
        )
      }
  }

  def deltaAmountForAddress(
      address: Address,
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
      address: Address,
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
