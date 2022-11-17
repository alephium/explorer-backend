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

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.api.model.{Address, Input, Output}
import org.alephium.util.{I256, U256}

object UtxoUtil {
  def amountForAddressInInputs(address: Address, inputs: ArraySeq[Input]): U256 = {
    inputs
      .filter(_.address == Some(address))
      .map(_.attoAlphAmount)
      .collect { case Some(amount) => amount }
      .fold(U256.Zero)(_ addUnsafe _)
  }

  def amountForAddressInOutputs(address: Address, outputs: ArraySeq[Output]): U256 = {
    outputs
      .filter(_.address == address)
      .map(_.attoAlphAmount)
      .fold(U256.Zero)(_ addUnsafe _)
  }

  def deltaAmountForAddress(address: Address,
                            inputs: ArraySeq[Input],
                            outputs: ArraySeq[Output]): Option[I256] = {
    for {
      in    <- I256.fromU256(amountForAddressInInputs(address, inputs))
      out   <- I256.fromU256(amountForAddressInOutputs(address, outputs))
      delta <- out.sub(in)
    } yield {
      delta
    }
  }
}
