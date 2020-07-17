package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class AddressInfo(balance: Long, transactions: Seq[Transaction])

object AddressInfo {
  implicit val codec: Codec[AddressInfo] = deriveCodec[AddressInfo]
}
