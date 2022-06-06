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
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

import com.typesafe.config.{Config, ConfigFactory}

import org.alephium.explorer.error.ExplorerError.{EmptyApplicationConfig, InvalidApplicationConfig}

/**
  * A parser for `application.conf` file. [[ApplicationConfig]] is a one-to-one representation
  * of types read by `typesafe.Config`.
  *
  * Reading `application.conf` via `typesafe.Config` is unsafe and therefore
  * should be used in this file only.
  *
  * [[ApplicationConfig]] can be parsed into [[ExplorerConfig]]
  */
case object ApplicationConfig {

  //BlockFlow configuration keys
  val KEY_BLOCK_FLOW_GROUP_NUM            = "blockflow.groupNum"
  val KEY_BLOCK_FLOW_DIRECT_CLIQUE_ACCESS = "blockflow.direct-clique-access"
  val KEY_BLOCK_FLOW_HOST                 = "blockflow.host"
  val KEY_BLOCK_FLOW_PORT                 = "blockflow.port"
  val KEY_BLOCK_FLOW_NETWORK_ID           = "blockflow.network-id"
  val KEY_BLOCK_FLOW_API_KEY              = "blockflow.api-key"
  //explorer configuration keys
  val KEY_EXPLORER_PORT                             = "explorer.port"
  val KEY_EXPLORER_HOST                             = "explorer.host"
  val KEY_EXPLORER_READ_ONLY                        = "explorer.readOnly"
  val KEY_EXPLORER_SYNC_PERIOD                      = "explorer.syncPeriod"
  val KEY_EXPLORER_TOKEN_SUPPLY_SERVICE_SYNC_PERIOD = "explorer.tokenSupplyServiceSyncPeriod"
  val KEY_EXPLORER_HASH_RATE_SERVICE_SYNC_PERIOD    = "explorer.hashRateServiceSyncPeriod"
  val KEY_EXPLORER_FINALIZER_SERVICE_SYNC_PERIOD    = "explorer.finalizerServiceSyncPeriod"
  val KEY_EXPLORER_TRANSACTION_HISTORY_SERVICE_SYNC_PERIOD =
    "explorer.transactionHistoryServiceSyncPeriod"

  def load(): Try[ApplicationConfig] =
    ApplicationConfig(ConfigFactory.load())

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(config: Config): Try[ApplicationConfig] =
    for {
      conf <- validateConfig(config)
      //BlockFlow configs
      blockFlowGroupNum           <- safe(KEY_BLOCK_FLOW_GROUP_NUM, conf.getInt)
      blockFlowDirectCliqueAccess <- safe(KEY_BLOCK_FLOW_DIRECT_CLIQUE_ACCESS, conf.getBoolean)
      blockFlowHost               <- safe(KEY_BLOCK_FLOW_HOST, conf.getString)
      blockFlowPort               <- safe(KEY_BLOCK_FLOW_PORT, conf.getInt)
      blockFlowNetworkId          <- safe(KEY_BLOCK_FLOW_NETWORK_ID, conf.getInt)
      blockFlowApiKey <- safe(
        KEY_BLOCK_FLOW_API_KEY,
        key => Option.when(config.hasPath(key))(config.getString(key))
      )
      //explorer configs
      explorerPort       <- safe(KEY_EXPLORER_PORT, conf.getInt)
      explorerHost       <- safe(KEY_EXPLORER_HOST, conf.getString)
      explorerReadOnly   <- safe(KEY_EXPLORER_READ_ONLY, conf.getBoolean)
      explorerSyncPeriod <- safe(KEY_EXPLORER_SYNC_PERIOD, conf.getDuration)
      tokenSupplyServiceSyncPeriod <- safe(KEY_EXPLORER_TOKEN_SUPPLY_SERVICE_SYNC_PERIOD,
                                           conf.getDuration)
      hashRateServiceSyncPeriod <- safe(KEY_EXPLORER_HASH_RATE_SERVICE_SYNC_PERIOD,
                                        conf.getDuration)
      finalizerServiceSyncPeriod <- safe(KEY_EXPLORER_FINALIZER_SERVICE_SYNC_PERIOD,
                                         conf.getDuration)
      transactionHistoryServicePeriod <- safe(KEY_EXPLORER_TRANSACTION_HISTORY_SERVICE_SYNC_PERIOD,
                                              conf.getDuration)
    } yield
      ApplicationConfig(
        blockFlowGroupNum               = blockFlowGroupNum,
        blockFlowDirectCliqueAccess     = blockFlowDirectCliqueAccess,
        blockFlowHost                   = blockFlowHost,
        blockFlowPort                   = blockFlowPort,
        blockFlowNetworkId              = blockFlowNetworkId,
        blockFlowApiKey                 = blockFlowApiKey,
        explorerHost                    = explorerHost,
        explorerPort                    = explorerPort,
        explorerReadOnly                = explorerReadOnly,
        explorerSyncPeriod              = explorerSyncPeriod.toScala,
        tokenSupplyServiceSyncPeriod    = tokenSupplyServiceSyncPeriod.toScala,
        hashRateServiceSyncPeriod       = hashRateServiceSyncPeriod.toScala,
        finalizerServiceSyncPeriod      = finalizerServiceSyncPeriod.toScala,
        transactionHistoryServicePeriod = transactionHistoryServicePeriod.toScala
      )

  @inline def validateConfig(config: Config): Try[Config] =
    if (config.isEmpty) {
      Failure(EmptyApplicationConfig())
    } else {
      Success(config)
    }

  /** A safe wrapper for calling `conf.get*` functions */
  @inline private def safe[A](key: String, parser: String => A): Try[A] =
    try Success(parser(key))
    catch {
      case throwable: Throwable =>
        Failure(InvalidApplicationConfig(key, throwable))
    }
}

final case class ApplicationConfig(blockFlowGroupNum: Int,
                                   blockFlowDirectCliqueAccess: Boolean,
                                   blockFlowHost: String,
                                   blockFlowPort: Int,
                                   blockFlowNetworkId: Int,
                                   blockFlowApiKey: Option[String],
                                   explorerHost: String,
                                   explorerPort: Int,
                                   explorerReadOnly: Boolean,
                                   explorerSyncPeriod: FiniteDuration,
                                   tokenSupplyServiceSyncPeriod: FiniteDuration,
                                   hashRateServiceSyncPeriod: FiniteDuration,
                                   finalizerServiceSyncPeriod: FiniteDuration,
                                   transactionHistoryServicePeriod: FiniteDuration)
