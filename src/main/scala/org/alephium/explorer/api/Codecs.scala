package org.alephium.explorer.api

import sttp.tapir.{Codec, Validator}
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.util.TimeStamp

object Codecs {
  implicit val timestampCodec: Codec[String, TimeStamp, TextPlain] =
    Codec.long.validate(Validator.min(0L)).map(TimeStamp.unsafe(_))(_.millis)
}
