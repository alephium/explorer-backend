// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.config

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.TryValues._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import org.alephium.api.model.ApiKey
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenCommon._
import org.alephium.explorer.config.ExplorerConfig._
import org.alephium.explorer.error.ExplorerError._
import org.alephium.protocol.model.NetworkId
import org.alephium.util.{Duration, TimeStamp}

class ExplorerConfigSpec extends AlephiumSpec with ScalaCheckDrivenPropertyChecks {

  "ficus" should {
    "load config" in {
      // We make sure every config file is valid
      val mainnetForkTimestamps = Seq(
        TimeStamp.unsafe(1718186400000L),      // rhone
        TimeStamp.unsafe(9000000000000000000L) // Danube
      )
      val testnetForkTimestamps = Seq(
        TimeStamp.unsafe(1715428800000L),      // rhone
        TimeStamp.unsafe(9000000000000000000L) // Danube
      )
      val devnetForkTimestamps = Seq(
        TimeStamp.unsafe(1695571200000L), // rhone
        TimeStamp.unsafe(1732666200000L)  // Danube
      )

      Map(
        NetworkId.AlephiumMainNet -> mainnetForkTimestamps,
        NetworkId.AlephiumTestNet -> testnetForkTimestamps,
        NetworkId.AlephiumDevNet  -> devnetForkTimestamps
      )
        .foreachEntry { case (networkId, forkTimestamps) =>
          val typesafeConfig =
            ConfigFactory.parseResources(s"application-${networkId.networkType}.conf").resolve()

          val consensus = typesafeConfig.as[ExplorerConfig]("alephium").consensus

          consensus.mainnet.forkTimestamp is TimeStamp.zero
          consensus.mainnet.blockTargetTime is Duration.ofSecondsUnsafe(64)

          consensus.rhone.forkTimestamp is forkTimestamps.head
          consensus.rhone.blockTargetTime is Duration.ofSecondsUnsafe(16)

          consensus.danube.forkTimestamp is forkTimestamps.last
          consensus.danube.blockTargetTime is Duration.ofSecondsUnsafe(8)
        }
    }
  }

  "validateGroupNum" should {
    "fail validation" when {
      "group numbers are negative" in {
        forAll(Gen.negNum[Int]) { negativeNum =>
          validateGroupNum(negativeNum).failure.exception is InvalidGroupNumber(negativeNum)
        }
      }
    }

    "pass validation" when {
      "positive groupNum" in {
        forAll(Gen.choose(1, 100)) { positiveNum =>
          validateGroupNum(positiveNum).success.value is positiveNum
        }
      }
    }
  }

  "validatePort" should {
    "fail validation" when {
      "portNums are negative" in {
        forAll(Gen.negNum[Int]) { negativeNum =>
          validatePort(negativeNum).failure.exception is InvalidPortNumber(negativeNum)
        }
      }

      "portNum = 0" in {
        validatePort(0).failure.exception is InvalidPortNumber(0)
      }

      "portNum > 65,535" in {
        validatePort(65536).failure.exception is InvalidPortNumber(65536)
      }
    }

    "pass validation" when {
      "port number is between 1 & 65_535 (inclusive)" in {
        forAll(Gen.choose(1, 65535)) { port =>
          validatePort(port).success.value is port
        }
      }
    }
  }

  "validateHost" should {
    "fail validation" when {
      "host is empty" in {
        validateHost("blah").failure.exception is a[InvalidHost]
        validateHost("1234_ABCD").failure.exception is a[InvalidHost]
        validateHost("local host").failure.exception is a[InvalidHost]
        validateHost("120 0 0 1").failure.exception is a[InvalidHost]
      }
    }

    "pass validation" when {
      "host is local" in {
        validateHost("localhost").success.value should be("localhost")
        validateHost("120.0.0.1").success.value should be("120.0.0.1")
      }
    }
  }

  "validateScheme" should {
    "pass validation" when {
      "with suported scheme" in {
        validateScheme("http").success.value is "http"
        validateScheme("https").success.value is "https"
      }
    }

    "fail validation" when {
      "scheme isn't supported" in {
        validateScheme("ftp").failure.exception is InvalidScheme("ftp")
        validateScheme("ws").failure.exception is InvalidScheme("ws")
        validateScheme("wss").failure.exception is InvalidScheme("wss")
        validateScheme("file").failure.exception is InvalidScheme("file")

        forAll(genStringOfLength(5)) { string =>
          validateScheme(string).failure.exception is InvalidScheme(string)
        }
      }
    }
  }

  "validateNetworkId" should {
    "fail validation" when {
      "networkId is greater than Byte.MaxValue" in {
        validateNetworkId(Byte.MaxValue + 1).failure.exception is
          InvalidNetworkId(Byte.MaxValue + 1)
      }

      "networkId is less than Byte.MinValue" in {
        validateNetworkId(Byte.MinValue - 1).failure.exception is
          InvalidNetworkId(Byte.MinValue - 1)
      }
    }

    "pass validation" when {
      "Byte.MinValue <= networkId <= Byte.MaxValue" in {
        forAll(Arbitrary.arbitrary[Byte]) { byte =>
          validateNetworkId(byte.toInt).success.value is NetworkId(byte)
        }
      }
    }
  }

  "validateApiKey" should {
    "fail validation" when {
      "apiKey.length < 32" in {
        forAll(genStringOfLength(31)) { string =>
          validateApiKey(string).failure.exception is InvalidApiKey(
            "Api key must have at least 32 characters"
          )
        }
      }
    }

    "pass validation" when {
      "apiKey.length > 32" in {
        forAll(genStringOfLengthBetween(32, 100)) { string =>
          validateApiKey(string).success.value is ApiKey.unsafe(string)
        }
      }
    }
  }

  "validateSyncPeriod" should {
    "fail validation" when {
      "interval is negative" in {
        validateSyncPeriod(-1.second).failure.exception is InvalidSyncPeriod(-1.second)
      }

      "interval is now" in {
        validateSyncPeriod(0.seconds).failure.exception is InvalidSyncPeriod(0.seconds)
      }
    }

    "pass validation" when {
      "interval is in the future" in {
        validateSyncPeriod(1.second).success.value is 1.second
        validateSyncPeriod(1.hour).success.value is 1.hour
        validateSyncPeriod(1.day).success.value is 1.day
      }
    }
  }
}
