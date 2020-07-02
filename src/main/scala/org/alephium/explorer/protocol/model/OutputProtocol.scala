package org.alephium.explorer.protocol.model

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.explorer.api.model.{Address, Output}
import org.alephium.rpc.CirceUtils._
import org.alephium.serde._

final case class OutputProtocol(
    amount: Long,
    createdHeight: Int,
    address: Address
) {
  lazy val toApi: Output = {
    Output(
      amount,
      createdHeight,
      address
    )
  }
}

object OutputProtocol {

  final case class Ref(scriptHint: Int, key: Hash) {
    def toApi: Output.Ref = Output.Ref(scriptHint, key)
  }

  object Ref {
    implicit val codec: Codec[Ref] = deriveCodec[Ref]
  }
  implicit val codec: Codec[OutputProtocol] = deriveCodec[OutputProtocol]

  def fromSerde[T: Serde]: Codec[T] = {
    def encoder: Encoder[T] = byteStringEncoder.contramap(serialize[T])
    val decoder: Decoder[T] = byteStringDecoder.emap(deserialize[T](_).left.map(_.getMessage))

    Codec.from(decoder, encoder)
  }
}
