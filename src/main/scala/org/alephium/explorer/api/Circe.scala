package org.alephium.explorer.api

import io.circe.{Codec, Decoder, Encoder}

import org.alephium.util.TimeStamp

object Circe {
  implicit val timestampDecoder: Decoder[TimeStamp] =
    Decoder.decodeLong.ensure(_ >= 0, s"expect positive timestamp").map(TimeStamp.unsafe)
  implicit val timestampEncoder: Encoder[TimeStamp] = Encoder.encodeLong.contramap(_.millis)
  implicit val timestampCodec: Codec[TimeStamp]     = Codec.from(timestampDecoder, timestampEncoder)
}
