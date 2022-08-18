// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer.api.model

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.json.Json._
import org.alephium.protocol
import org.alephium.serde._
import org.alephium.util.AVector

sealed trait UnlockScript {
  def p2pkhAddress: Option[Address]
}

object UnlockScript {

  @upickle.implicits.key("p2pkh")
  final case class P2PKH(address: Address) extends UnlockScript {
    val p2pkhAddress: Option[Address] = Some(address)
  }

  @upickle.implicits.key("p2mpkh")
  final case class P2MPKH(indexedAddresses: Seq[P2MPKH.IndexedAddress]) extends UnlockScript {
    val p2pkhAddress: Option[Address] = None
  }

  object P2MPKH {
    final case class IndexedAddress(address: Address, index: Int)
    implicit val indexedAddressReadWriter: ReadWriter[IndexedAddress] = macroRW
    implicit val indexedAddressesSerde: Serde[IndexedAddress] = Serde
      .tuple2[Address, Int]
      .xmap({ case (address, index) => IndexedAddress(address, index) },
            indexedAddress => (indexedAddress.address, indexedAddress.index))
  }

  @upickle.implicits.key("p2sh")
  final case class P2SH(script: ByteString, params: ByteString) extends UnlockScript {
    val p2pkhAddress: Option[Address] = None
  }

  implicit val p2pkhReadWriter: ReadWriter[P2PKH]   = macroRW
  implicit val p2mpkhReadWriter: ReadWriter[P2MPKH] = macroRW
  implicit val p2shReadWriter: ReadWriter[P2SH]     = macroRW
  implicit val readWriter: ReadWriter[UnlockScript] =
    ReadWriter.merge(p2pkhReadWriter, p2mpkhReadWriter, p2shReadWriter)

  def fromProtocol(unlockScript: protocol.vm.UnlockScript): UnlockScript =
    unlockScript match {
      case protocol.vm.UnlockScript.P2PKH(pk) =>
        val address = Address.unsafe(protocol.model.Address.p2pkh(pk).toBase58)
        P2PKH(address)
      case protocol.vm.UnlockScript.P2MPKH(pks) =>
        val addresses = pks.map {
          case (pk, index) =>
            P2MPKH.IndexedAddress(Address.unsafe(protocol.model.Address.p2pkh(pk).toBase58), index)
        }.toSeq
        P2MPKH(addresses)
      case protocol.vm.UnlockScript.P2SH(script, params) =>
        P2SH(serialize(script), serialize(params))
    }

  implicit val serde: Serde[UnlockScript] = {

    val p2mpkhSerde: Serde[P2MPKH] =
      Serde
        .forProduct1[AVector[P2MPKH.IndexedAddress], P2MPKH](vec => P2MPKH.apply(vec.toSeq),
                                                             t   => AVector.from(t.indexedAddresses))
    val p2shSerde: Serde[P2SH] = Serde.forProduct2(P2SH, t => (t.script, t.params))

    new Serde[UnlockScript] {
      override def serialize(input: UnlockScript): ByteString = {
        input match {
          case p2pkh: P2PKH   => ByteString(0) ++ serdeImpl[Address].serialize(p2pkh.address)
          case p2mpkh: P2MPKH => ByteString(1) ++ p2mpkhSerde.serialize(p2mpkh)
          case p2sh: P2SH     => ByteString(2) ++ p2shSerde.serialize(p2sh)
        }
      }

      override def _deserialize(input: ByteString): SerdeResult[Staging[UnlockScript]] = {
        byteSerde._deserialize(input).flatMap {
          case Staging(0, content) =>
            serdeImpl[Address]._deserialize(content).map(_.mapValue(P2PKH))
          case Staging(1, content) => p2mpkhSerde._deserialize(content)
          case Staging(2, content) => p2shSerde._deserialize(content)
          case Staging(n, _)       => Left(SerdeError.wrongFormat(s"Invalid unlock script prefix $n"))
        }
      }
    }
  }
}
