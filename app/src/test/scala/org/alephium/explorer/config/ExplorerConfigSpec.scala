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

package org.alephium.explorer.config

import scala.concurrent.duration._
import scala.util.{Success, Try}

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

class ExplorerConfigSpec extends AlephiumSpec with ScalaCheckDrivenPropertyChecks {

  "ficus" should {
    "load config" in {
      val typesafeConfig = ConfigFactory.load()
      Try(typesafeConfig.as[ExplorerConfig]("alephium")) is a[Success[ExplorerConfig]]
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
