package org.alephium.explorer.api

import sttp.tapir.{Codec, DecodeResult}
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.util.TimeStamp

object Codecs {
  implicit val timestampCodec: Codec[String, TimeStamp, TextPlain] = Codec.long
    .mapDecode(millis =>
      TimeStamp.from(millis) match {
        case Some(timestamp) => DecodeResult.Value(timestamp)
        case None            => DecodeResult.Error(s"$millis", new Throwable("timestamp must be > 0"))
    })(_.millis)
}
