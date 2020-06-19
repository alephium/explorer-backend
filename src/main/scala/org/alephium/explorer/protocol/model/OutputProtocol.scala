package org.alephium.explorer.protocol.model

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Output
import org.alephium.protocol.script.{OP_PUSH, PubScript}
import org.alephium.rpc.CirceUtils._
import org.alephium.serde._

final case class OutputProtocol(
    pubScript: PubScript,
    value: Long
) {
  lazy val toApi: Output = {
    Output(
      pubScript.instructions.toArray
        .collectFirst { case OP_PUSH(bytes) => Hash.from(bytes) }
        .flatten
        .getOrElse(pubScript.hash),
      value
    )
  }
}

object OutputProtocol {
  implicit val pubScriptCodec: Codec[PubScript] = fromSerde[PubScript]
  implicit val codec: Codec[OutputProtocol]     = deriveCodec[OutputProtocol]

  private def fromSerde[T: Serde]: Codec[T] = {
    def encoder: Encoder[T] = byteStringEncoder.contramap(serialize[T])
    val decoder: Decoder[T] = byteStringDecoder.emap(deserialize[T](_).left.map(_.getMessage))

    Codec.from(decoder, encoder)
  }
}
