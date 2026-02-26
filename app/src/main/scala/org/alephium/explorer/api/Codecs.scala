// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.util.{Failure, Success, Try}

import sttp.tapir.{Codec, CodecFormat, DecodeResult}
import sttp.tapir.Codec.PlainCodec
import upickle.core.Abort

import org.alephium.api.TapirCodecs
import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.config.Default
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig

object Codecs extends TapirCodecs {

  implicit val groupConfig: GroupConfig = Default.groupConfig

  implicit val grouplessAddressRW: ReadWriter[ApiAddress] = readwriter[String].bimap(
    _.toBase58,
    input => ApiAddress.fromBase58(input).getOrElse(throw Abort(s"Cannot parse Address: $input"))
  )

  implicit val explorerAddressTapirCodec: PlainCodec[ApiAddress] = fromJson[ApiAddress]

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  ) // Wartremover is complaining, maybe beacause of tapir macros
  implicit val intervalTypeCodec: PlainCodec[IntervalType] =
    Codec.derivedEnumeration[String, IntervalType](
      IntervalType.validate,
      _.string
    )

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  ) // Wartremover is complaining, maybe beacause of tapir macros
  def intervalTypeSubsetCodec(subset: Set[IntervalType]): PlainCodec[IntervalType] =
    Codec.string.mapDecode[IntervalType] { raw =>
      IntervalType.validate(raw) match {
        case Some(intervalType) if subset.contains(intervalType) => DecodeResult.Value(intervalType)
        case _ =>
          DecodeResult.Error(
            raw,
            new IllegalArgumentException(
              s"allowed values: ${subset.map(_.string).mkString(", ")}, but got"
            )
          )
      }
    }(_.string)

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  ) // Wartremover is complaining, maybe beacause of tapir macros
  implicit val txStatusTypeCodec: PlainCodec[TxStatusType] =
    Codec.derivedEnumeration[String, TxStatusType](
      TxStatusType.validate,
      _.string
    )

  implicit val tokenStdInterfaceIdCodec: Codec[String, TokenStdInterfaceId, CodecFormat.TextPlain] =
    fromJson[TokenStdInterfaceId](
      StdInterfaceId.tokenReadWriter,
      StdInterfaceId.tokenWithHexStringSchema
    )

  def explorerFromJson[A: ReadWriter]: PlainCodec[A] =
    Codec.string.mapDecode[A] { raw =>
      Try(read[A](ujson.Str(raw))) match {
        case Success(a) => DecodeResult.Value(a)
        case Failure(error) =>
          DecodeResult.Error(raw, new IllegalArgumentException(error.getMessage))
      }
    } { a =>
      writeJs(a) match {
        case ujson.Str(str) => str
        case other          => write(other)
      }
    }
}
