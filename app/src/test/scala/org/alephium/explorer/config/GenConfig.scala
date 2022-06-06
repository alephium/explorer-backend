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

import scala.jdk.CollectionConverters.MapHasAsJava

import com.typesafe.config.{Config, ConfigFactory}
import org.scalacheck.{Arbitrary, Gen}

import org.alephium.explorer.GenCommon._
import org.alephium.explorer.config.ApplicationConfig._

/** Test functions for config related files */
object GenConfig {

  /** A Map containing all configurations */
  def genRawConfigMap(): Gen[Map[String, _]] =
    for {
      groupNum                            <- Arbitrary.arbitrary[Int]
      cliqueAccess                        <- Arbitrary.arbitrary[Boolean]
      explorerHost                        <- Arbitrary.arbitrary[String]
      explorerPort                        <- Arbitrary.arbitrary[Int]
      networkId                           <- Arbitrary.arbitrary[Int]
      apiKey                              <- Arbitrary.arbitrary[String]
      host                                <- Arbitrary.arbitrary[String]
      port                                <- Arbitrary.arbitrary[Int]
      readOnly                            <- Arbitrary.arbitrary[Boolean]
      syncPeriod                          <- genTimeDurationForConfigString
      tokenSupplyServiceSyncPeriod        <- genTimeDurationForConfigString
      hashRateServiceSyncPeriod           <- genTimeDurationForConfigString
      finalizerServiceSyncPeriod          <- genTimeDurationForConfigString
      transactionHistoryServiceSyncPeriod <- genTimeDurationForConfigString
    } yield
      Map(
        KEY_BLOCK_FLOW_GROUP_NUM                             -> groupNum,
        KEY_BLOCK_FLOW_DIRECT_CLIQUE_ACCESS                  -> cliqueAccess,
        KEY_BLOCK_FLOW_HOST                                  -> explorerHost,
        KEY_BLOCK_FLOW_PORT                                  -> explorerPort,
        KEY_BLOCK_FLOW_NETWORK_ID                            -> networkId,
        KEY_BLOCK_FLOW_API_KEY                               -> apiKey,
        KEY_EXPLORER_PORT                                    -> port,
        KEY_EXPLORER_HOST                                    -> host,
        KEY_EXPLORER_READ_ONLY                               -> readOnly,
        KEY_EXPLORER_SYNC_PERIOD                             -> syncPeriod,
        KEY_EXPLORER_TOKEN_SUPPLY_SERVICE_SYNC_PERIOD        -> tokenSupplyServiceSyncPeriod,
        KEY_EXPLORER_HASH_RATE_SERVICE_SYNC_PERIOD           -> hashRateServiceSyncPeriod,
        KEY_EXPLORER_FINALIZER_SERVICE_SYNC_PERIOD           -> finalizerServiceSyncPeriod,
        KEY_EXPLORER_TRANSACTION_HISTORY_SERVICE_SYNC_PERIOD -> transactionHistoryServiceSyncPeriod
      )

  def parseMap(map: Map[String, _]): Config =
    ConfigFactory.parseMap(map.asJava)

  def genRawConfig(): Gen[Config] =
    genRawConfigMap().map(parseMap)

  /** List of all configuration keys */
  def allConfigKeys(): List[String] =
    List(
      KEY_BLOCK_FLOW_GROUP_NUM,
      KEY_BLOCK_FLOW_DIRECT_CLIQUE_ACCESS,
      KEY_BLOCK_FLOW_HOST,
      KEY_BLOCK_FLOW_PORT,
      KEY_BLOCK_FLOW_NETWORK_ID,
      KEY_BLOCK_FLOW_API_KEY,
      KEY_EXPLORER_PORT,
      KEY_EXPLORER_HOST,
      KEY_EXPLORER_READ_ONLY,
      KEY_EXPLORER_SYNC_PERIOD,
      KEY_EXPLORER_TOKEN_SUPPLY_SERVICE_SYNC_PERIOD,
      KEY_EXPLORER_HASH_RATE_SERVICE_SYNC_PERIOD,
      KEY_EXPLORER_FINALIZER_SERVICE_SYNC_PERIOD,
      KEY_EXPLORER_TRANSACTION_HISTORY_SERVICE_SYNC_PERIOD
    )

}
