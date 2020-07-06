package org.alephium.explorer.api

import io.circe.{Codec, Decoder, Encoder, Json}

import org.alephium.explorer.Hash
import org.alephium.rpc.CirceUtils.byteStringDecoder

object Circe {
  implicit val hashEncoder: Encoder[Hash] = hash => Json.fromString(hash.toHexString)
  implicit val hashDecoder: Decoder[Hash] =
    byteStringDecoder.emap(Hash.from(_).toRight("cannot decode hash"))
  implicit val hashCodec: Codec[Hash] = Codec.from(hashDecoder, hashEncoder)
}
