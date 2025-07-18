// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model.{Input, Output, Token}
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

object UtxoUtil {

  def addressEqual(address: Address, apiAddress: ApiAddress)(implicit
      groupConfig: GroupConfig
  ): Boolean = {
    apiAddress.lockupScript match {
      case ApiAddress.CompleteLockupScript(_) =>
        address.toBase58 == apiAddress.toBase58
      case _: ApiAddress.HalfDecodedLockupScript =>
        address.toBase58.dropRight(ApiAddress.GrouplessSuffixSize) == apiAddress.toBase58
    }
  }

  def amountForAddressInInputs(address: ApiAddress, inputs: ArraySeq[Input])(implicit
      groupConfig: GroupConfig
  ): Option[U256] =
    sumAmounts(
      inputs
        .filter(_.address.exists(a => addressEqual(a, address)))
        .map(_.attoAlphAmount)
        .collect { case Some(amount) => amount }
    )

  def amountForAddressInOutputs(address: ApiAddress, outputs: ArraySeq[Output])(implicit
      groupConfig: GroupConfig
  ): Option[U256] =
    sumAmounts(
      outputs
        .filter(o => addressEqual(o.address, address))
        .map(_.attoAlphAmount)
    )

  def tokenAmountForAddressInInputs(
      address: ApiAddress,
      inputs: ArraySeq[Input]
  )(implicit groupConfig: GroupConfig): Map[TokenId, Option[U256]] =
    sumTokensById(
      inputs
        .filter(_.address.exists(a => addressEqual(a, address)))
        .flatMap(_.tokens)
        .flatten
    )

  def tokenAmountForAddressInOutputs(
      address: ApiAddress,
      outputs: ArraySeq[Output]
  )(implicit groupConfig: GroupConfig): Map[TokenId, Option[U256]] =
    sumTokensById(
      outputs
        .filter(o => addressEqual(o.address, address))
        .flatMap(_.tokens)
        .flatten
    )

  def deltaAmountForAddress(
      address: ApiAddress,
      inputs: ArraySeq[Input],
      outputs: ArraySeq[Output]
  )(implicit groupConfig: GroupConfig): Option[BigInteger] = {
    for {
      in  <- amountForAddressInInputs(address, inputs)
      out <- amountForAddressInOutputs(address, outputs)
    } yield {
      out.v.subtract(in.v)
    }
  }

  def deltaTokenAmountForAddress(
      address: ApiAddress,
      inputs: ArraySeq[Input],
      outputs: ArraySeq[Output]
  )(implicit groupConfig: GroupConfig): Map[TokenId, BigInteger] = {
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
