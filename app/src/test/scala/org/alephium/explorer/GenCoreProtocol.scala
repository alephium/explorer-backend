// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import org.alephium.crypto.{ED25519PublicKey, SecP256K1PublicKey, SecP256R1PublicKey}
import org.alephium.explorer.GenCommon._
import org.alephium.protocol.{ALPH, Hash, PrivateKey, PublicKey, SignatureSchema}
import org.alephium.protocol.model._
import org.alephium.protocol.vm.{
  Instr,
  LockupScript,
  Method,
  PublicKeyLike,
  StatefulContract,
  StatelessContext,
  StatelessScript,
  UnlockScript
}
import org.alephium.util.AVector

@SuppressWarnings(
  Array(
    "org.wartremover.warts.JavaSerializable",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"
  )
)
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

  def publicKeyLikeGen(): Gen[PublicKeyLike] = {
    Gen
      .oneOf(
        Gen.const(()).map(_ => PublicKeyLike.SecP256K1(SecP256K1PublicKey.generate)),
        Gen.const(()).map(_ => PublicKeyLike.SecP256R1(SecP256R1PublicKey.generate)),
        Gen.const(()).map(_ => PublicKeyLike.ED25519(ED25519PublicKey.generate)),
        Gen.const(()).map(_ => PublicKeyLike.WebAuthn(SecP256R1PublicKey.generate))
      )
  }

  def p2pkhLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2PKH] =
    for {
      publicKey <- publicKeyGen(groupIndex)
    } yield LockupScript.p2pkh(publicKey)

  def p2pkLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2PK] =
    for {
      // TODO: currently we can't define another group than the default one, as the `toBase58` produce same result for all groups
      _         <- Gen.option(GenApiModel.groupIndexGen)
      publicKey <- publicKeyGen(groupIndex).map(PublicKeyLike.SecP256K1(_))
    } yield LockupScript.p2pk(publicKey, publicKey.defaultGroup(groupSetting.groupConfig))

  def p2hmpkLockupGen(
      groupIndex: GroupIndex
  ): Gen[LockupScript.P2HMPK] =
    for {
      numKeys   <- Gen.chooseNum(1, ALPH.MaxKeysInP2MPK)
      keys      <- Gen.listOfN(numKeys, publicKeyLikeGen()).map(AVector.from)
      threshold <- Gen.choose(1, keys.length)
    } yield LockupScript.P2HMPK(keys, threshold, groupIndex).toOption.get

  def p2mpkhLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2MPKH] =
    for {
      numKeys   <- Gen.chooseNum(1, ALPH.MaxKeysInP2MPK)
      keys      <- Gen.listOfN(numKeys, publicKeyGen(groupIndex)).map(AVector.from)
      threshold <- Gen.choose(1, keys.length)
    } yield LockupScript.p2mpkh(keys, threshold).get

  def p2mpkhLockupGen(n: Int, m: Int, groupIndex: GroupIndex)(implicit
      groupSetting: GroupSetting
  ): Gen[LockupScript.P2MPKH] = {
    assume(m <= n)
    for {
      publicKey0 <- publicKeyGen(groupIndex)
      moreKeys   <- Gen.listOfN(n, publicKeyGen(groupIndex)).map(AVector.from)
    } yield LockupScript.p2mpkh(publicKey0 +: moreKeys, m).get
  }

  def p2shLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript.P2SH] = {
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
      p2hmpkLockupGen(groupIndex),
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

  def halfDecodedLockupGen(
      groupIndex: GroupIndex
  )(implicit groupSetting: GroupSetting): Gen[LockupScript] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2pkLockupGen(groupIndex)
    )
  }

  def lockupGen(groupIndex: GroupIndex)(implicit groupSetting: GroupSetting): Gen[LockupScript] = {
    Gen.oneOf(
      p2pkhLockupGen(groupIndex),
      p2pkLockupGen(groupIndex),
      p2mpkhLockupGen(groupIndex),
      p2hmpkLockupGen(groupIndex),
      p2shLockupGen(groupIndex),
      p2cLockupGen(groupIndex)
    )
  }

  def addressAssetProtocolGen()(implicit groupSetting: GroupSetting): Gen[Address.Asset] =
    for {
      group   <- Gen.choose(0, groupSetting.groupNum - 1)
      address <- assetLockupGen(new GroupIndex(group)).map(Address.Asset(_))
    } yield address

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
