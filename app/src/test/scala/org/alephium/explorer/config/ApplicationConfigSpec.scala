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

import scala.compat.java8.DurationConverters.DurationOps

import org.scalatest.TryValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import org.alephium.explorer.AlephiumSpec.IsOps
import org.alephium.explorer.config.ApplicationConfig._
import org.alephium.explorer.config.GenConfig._
import org.alephium.explorer.error.ExplorerError.{EmptyApplicationConfig, InvalidApplicationConfig}

class ApplicationConfigSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "read valid configuration" in {
    forAll(genRawConfig()) { raw =>
      val appConfig = ApplicationConfig(raw).success.value

      //BlockFlow configurations
      appConfig.blockFlowGroupNum is raw.getInt(KEY_BLOCK_FLOW_GROUP_NUM)
      appConfig.blockFlowDirectCliqueAccess is raw.getBoolean(KEY_BLOCK_FLOW_DIRECT_CLIQUE_ACCESS)
      appConfig.blockFlowPort is raw.getInt(KEY_BLOCK_FLOW_PORT)

      /** Off because IntelliJ is not happy with `is` on Strings */
      appConfig.blockFlowHost should be(raw.getString(KEY_BLOCK_FLOW_HOST))
      appConfig.blockFlowNetworkId is raw.getInt(KEY_BLOCK_FLOW_NETWORK_ID)
      appConfig.blockFlowApiKey is
        Option.when(raw.hasPath(KEY_BLOCK_FLOW_API_KEY))(raw.getString(KEY_BLOCK_FLOW_API_KEY))

      //explorer configurations
      appConfig.explorerHost should be(raw.getString(KEY_EXPLORER_HOST))
      appConfig.explorerPort is raw.getInt(KEY_EXPLORER_PORT)
      appConfig.explorerReadOnly is raw.getBoolean(KEY_EXPLORER_READ_ONLY)

      appConfig.explorerSyncPeriod is
        raw
          .getDuration(KEY_EXPLORER_SYNC_PERIOD)
          .toScala

      appConfig.tokenSupplyServiceSyncPeriod is
        raw
          .getDuration(KEY_EXPLORER_TOKEN_SUPPLY_SERVICE_SYNC_PERIOD)
          .toScala

      appConfig.hashRateServiceSyncPeriod is
        raw
          .getDuration(KEY_EXPLORER_HASH_RATE_SERVICE_SYNC_PERIOD)
          .toScala

      appConfig.finalizerServiceSyncPeriod is
        raw
          .getDuration(KEY_EXPLORER_FINALIZER_SERVICE_SYNC_PERIOD)
          .toScala

      appConfig.transactionHistoryServicePeriod is
        raw
          .getDuration(KEY_EXPLORER_TRANSACTION_HISTORY_SERVICE_SYNC_PERIOD)
          .toScala
    }
  }

  "report empty configuration" in {
    ApplicationConfig(parseMap(Map.empty)).failure.exception is a[EmptyApplicationConfig]
  }

  "report missing configuration" in {
    forAll(genRawConfigMap()) { rawMap =>
      allConfigKeys() foreach { keyToRemove =>
        val rawMapWithMissingKey = parseMap(rawMap - keyToRemove)
        val appConfig            = ApplicationConfig(rawMapWithMissingKey)

        //BlockFlow-api config is optional so it should be set to None
        if (keyToRemove == ApplicationConfig.KEY_BLOCK_FLOW_API_KEY) {
          appConfig.success.value.blockFlowApiKey is None
        } else {
          appConfig.failure.exception is a[InvalidApplicationConfig]
          //Exception message (Stacktrace) should include the missing config's name
          appConfig.failure.exception.getCause.getMessage should include(keyToRemove)
        }
      }
    }
  }
}
