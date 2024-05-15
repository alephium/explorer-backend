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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.explorer.GenCommon._
import org.alephium.protocol.{ALPH, Hash, PrivateKey, PublicKey, SignatureSchema}
import org.alephium.protocol.model._
import org.alephium.protocol.vm.{
  Instr,
  LockupScript,
  Method,
  StatefulContract,
  StatelessContext,
  StatelessScript,
  UnlockScript
}
import org.alephium.util.AVector

/** Generators for types supplied by Core `org.alephium.protocol` package */
object GenCoreProtocol {

  val hashGen: Gen[Hash]                     = Gen.const(()).map(_ => Hash.generate)
  val blockHashGen: Gen[BlockHash]           = Gen.const(()).map(_ => BlockHash.generate)
  val transactionHashGen: Gen[TransactionId] = Gen.const(()).map(_ => TransactionId.generate)
  def contractIdGen(implicit gs: GroupSetting): Gen[ContractId] = for {
    txId        <- transactionHashGen
    outputIndex <- Gen.posNum[Int]
    groupIndex  <- Gen.choose(0, gs.groupNum - 1).map(new GroupIndex(_))
  } yield {
    ContractId.from(txId, outputIndex, groupIndex)
  }

  val genNetworkId: Gen[NetworkId] =
    genBytePositive.map(NetworkId(_))

  def genNetworkId(exclude: NetworkId): Gen[NetworkId] =
    genNetworkId.filter(_ != exclude)

  def keyPairGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): (PrivateKey, PublicKey) = {
    val (privateKey, publicKey) = SignatureSchema.secureGeneratePriPub()
    val lockupScript            = LockupScript.p2pkh(Hash.hash(publicKey.bytes))
    if (lockupScript.groupIndex(groupSetting.groupConfig) == groupIndex) {
      (privateKey, publicKey)
    } else {
      keyPairGen(groupIndex)
    }
  }

  def publicKeyGen(groupIndex: GroupIndex)(implicit groupSetting: GroupSetting): Gen[PublicKey] =
    keyPairGen(groupIndex)._2

  def p2pkhLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2PKH] =
    for {
      publicKey <- publicKeyGen(groupIndex)
    } yield LockupScript.p2pkh(publicKey)

  def p2mpkhLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.Asset] =
    for {
      numKeys   <- Gen.chooseNum(1, ALPH.MaxKeysInP2MPK)
      keys      <- Gen.listOfN(numKeys, publicKeyGen(groupIndex)).map(AVector.from)
      threshold <- Gen.choose(1, keys.length)
    } yield LockupScript.p2mpkh(keys, threshold).get

  def p2mpkhLockupGen(n: Int, m: Int, groupIndex: GroupIndex)(implicit
      groupSetting: GroupSetting
  ): Gen[LockupScript.Asset] = {
    assume(m <= n)
    for {
      publicKey0 <- publicKeyGen(groupIndex)
      moreKeys   <- Gen.listOfN(n, publicKeyGen(groupIndex)).map(AVector.from)
    } yield LockupScript.p2mpkh(publicKey0 +: moreKeys, m).get
  }

  def p2shLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.Asset] = {
    hashGen
      .retryUntil { hash =>
        ScriptHint.fromHash(hash).groupIndex(groupSetting.groupConfig).equals(groupIndex)
      }
      .map(LockupScript.p2sh)
  }

  def assetLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.Asset] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2mpkhLockupGen(groupIndex),
      p2shLockupGen(groupIndex)
    )
  }

  def p2cLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2C] = {
    hashGen
      .retryUntil { hash =>
        ScriptHint.fromHash(hash).groupIndex(groupSetting.groupConfig).equals(groupIndex)
      }
      .map { hash =>
        LockupScript.p2c(ContractId.unsafe(hash))
      }
  }

  def lockupGen(groupIndex: GroupIndex)(implicit groupSetting: GroupSetting): Gen[LockupScript] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2mpkhLockupGen(groupIndex),
      p2shLockupGen(groupIndex),
      p2cLockupGen(groupIndex)
    )
  }

  def addressAssetProtocolGen()(implicit groupSetting: GroupSetting): Gen[Address.Asset] =
    for {
      group <- Gen.choose(0, groupSetting.groupNum - 1)
    } yield {
      val groupIndex     = GroupIndex.unsafe(group)(groupSetting.groupConfig)
      val (_, publicKey) = groupIndex.generateKey(groupSetting.groupConfig)
      Address.p2pkh(publicKey)
    }

  def addressContractProtocolGen(implicit groupSetting: GroupSetting): Gen[Address.Contract] =
    for {
      contractId <- contractIdGen
    } yield {
      Address.contract(contractId)
    }

  val keypairGen: Gen[(PrivateKey, PublicKey)] =
    Gen.const(()).map(_ => SignatureSchema.secureGeneratePriPub())

  val publicKeyGen: Gen[PublicKey] =
    keypairGen.map(_._2)

  val unlockScriptProtocolP2PKHGen: Gen[UnlockScript.P2PKH] =
    publicKeyGen.map(UnlockScript.p2pkh)

  val unlockScriptProtocolP2MPKHGen: Gen[UnlockScript.P2MPKH] =
    for {
      n          <- Gen.choose(5, 8)
      m          <- Gen.choose(2, 4)
      publicKey0 <- publicKeyGen
      moreKeys   <- Gen.listOfN(n, publicKeyGen)
      indexedKey <- Gen.pick(m, (publicKey0 +: moreKeys).zipWithIndex).map(AVector.from)
    } yield {
      UnlockScript.p2mpkh(indexedKey.sortBy(_._2))
    }

  val methodGen: Gen[Method[StatelessContext]] = {
    for {
      useContractAssets <- arbitrary[Boolean]
    } yield {
      Method(
        isPublic = true,
        usePreapprovedAssets = false,
        useContractAssets,
        usePayToContractOnly = false,
        argsLength = 0,
        localsLength = 0,
        returnLength = 0,
        instrs = AVector.empty[Instr[StatelessContext]]
      )
    }
  }

  val unlockScriptProtocolP2SHGen: Gen[UnlockScript.P2SH] = {
    for {
      methods <- Gen.listOfN(4, methodGen)
    } yield {
      val script = StatelessScript.unsafe(AVector.from(methods))
      UnlockScript.P2SH(script, AVector.empty)
    }
  }

  val unlockScriptProtocolGen: Gen[UnlockScript] =
    Gen.oneOf(
      unlockScriptProtocolP2PKHGen: Gen[UnlockScript],
      unlockScriptProtocolP2MPKHGen: Gen[UnlockScript]
    )

  val statefulContractGen: Gen[StatefulContract] =
    for {
      fieldLength <- Gen.posNum[Int]
    } yield StatefulContract(fieldLength, AVector.empty)

}
