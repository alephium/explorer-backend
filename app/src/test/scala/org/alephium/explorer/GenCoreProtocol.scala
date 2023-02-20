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

package org.alephium.explorer

import org.scalacheck.Gen

import org.alephium.explorer.GenApiModel.hashGen
import org.alephium.explorer.GenCommon._
import org.alephium.protocol.{ALPH, Hash, PrivateKey, PublicKey, SignatureSchema}
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{ContractId, GroupIndex, NetworkId, ScriptHint}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.AVector

/** Generators for types supplied by Core `org.alephium.protocol` package */
object GenCoreProtocol {

  private implicit val groupConfig: GroupConfig = Generators.groupSetting.groupConfig

  val genNetworkId: Gen[NetworkId] =
    genBytePositive.map(NetworkId(_))

  def genNetworkId(exclude: NetworkId): Gen[NetworkId] =
    genNetworkId.filter(_ != exclude)

  def keyPairGen(groupIndex: GroupIndex): (PrivateKey, PublicKey) = {
    val (privateKey, publicKey) = SignatureSchema.secureGeneratePriPub()
    val lockupScript            = LockupScript.p2pkh(Hash.hash(publicKey.bytes))
    if (lockupScript.groupIndex == groupIndex) {
      (privateKey, publicKey)
    } else {
      keyPairGen(groupIndex)
    }
  }

  def publicKeyGen(groupIndex: GroupIndex): Gen[PublicKey] = keyPairGen(groupIndex)._2

  def p2pkhLockupGen(groupIndex: GroupIndex): Gen[LockupScript.P2PKH] =
    for {
      publicKey <- publicKeyGen(groupIndex)
    } yield LockupScript.p2pkh(publicKey)

  def p2mpkhLockupGen(groupIndex: GroupIndex): Gen[LockupScript.Asset] =
    for {
      numKeys   <- Gen.chooseNum(1, ALPH.MaxKeysInP2MPK)
      keys      <- Gen.listOfN(numKeys, publicKeyGen(groupIndex)).map(AVector.from)
      threshold <- Gen.choose(1, keys.length)
    } yield LockupScript.p2mpkh(keys, threshold).get

  def p2mpkhLockupGen(n: Int, m: Int, groupIndex: GroupIndex): Gen[LockupScript.Asset] = {
    assume(m <= n)
    for {
      publicKey0 <- publicKeyGen(groupIndex)
      moreKeys   <- Gen.listOfN(n, publicKeyGen(groupIndex)).map(AVector.from)
    } yield LockupScript.p2mpkh(publicKey0 +: moreKeys, m).get
  }

  def p2shLockupGen(groupIndex: GroupIndex): Gen[LockupScript.Asset] = {
    hashGen
      .retryUntil { hash =>
        ScriptHint.fromHash(hash).groupIndex.equals(groupIndex)
      }
      .map(LockupScript.p2sh)
  }

  def assetLockupGen(groupIndex: GroupIndex): Gen[LockupScript.Asset] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2mpkhLockupGen(groupIndex),
      p2shLockupGen(groupIndex)
    )
  }

  def p2cLockupGen(groupIndex: GroupIndex): Gen[LockupScript.P2C] = {
    hashGen
      .retryUntil { hash =>
        ScriptHint.fromHash(hash).groupIndex.equals(groupIndex)
      }
      .map { hash =>
        LockupScript.p2c(ContractId.unsafe(hash))
      }
  }

  def lockupGen(groupIndex: GroupIndex): Gen[LockupScript] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2mpkhLockupGen(groupIndex),
      p2shLockupGen(groupIndex),
      p2cLockupGen(groupIndex)
    )
  }
}
