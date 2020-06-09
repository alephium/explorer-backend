package org.alephium.explorer.api

import sttp.tapir.{Codec, Validator}
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.explorer.Hash
import org.alephium.util.{Hex, TimeStamp}

object Codecs {
  implicit val timestampCodec: Codec[String, TimeStamp, TextPlain] =
    Codec.long.validate(Validator.min(0L)).map(TimeStamp.unsafe(_))(_.millis)

  implicit val hashCodec: Codec[String, Hash, TextPlain] =
    Codec.string
      .validate(Validator.custom(Hex.from(_).flatMap(Hash.from).isDefined, "cannot decode hash"))
      .map(raw => Hash.unsafe(Hex.unsafe(raw)))(_.toHexString)

}
