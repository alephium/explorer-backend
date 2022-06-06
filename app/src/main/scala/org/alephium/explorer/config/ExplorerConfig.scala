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

import java.net.InetAddress

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.http.scaladsl.model.Uri

import org.alephium.api.model.ApiKey
import org.alephium.explorer.error.ExplorerError._
import org.alephium.protocol.model.NetworkId

object ExplorerConfig {

  /**
    * Validates raw configurations read from `application.conf` via [[ApplicationConfig]]
    * and converts it to [[ExplorerConfig]].
    *
    * Validates all [[ApplicationConfig]] values before creating [[ExplorerConfig]]
    * which ensures that Explorer does not get started with invalid configurations.
    *
    * @param config Raw `application.conf` properties and values.
    *
    * @return A validated [[ExplorerConfig]].
    */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(config: ApplicationConfig): Try[ExplorerConfig] =
    for {
      groupNum                        <- validateGroupNum(config.blockFlowGroupNum)
      directCliqueAccess              <- Success(config.blockFlowDirectCliqueAccess)
      port                            <- validatePort(config.explorerPort)
      host                            <- validateHost(config.blockFlowHost)
      readOnly                        <- Success(config.explorerReadOnly)
      blockFlowUri                    <- validateUri(config.blockFlowHost, config.blockFlowPort)
      networkId                       <- validateNetworkId(config.blockFlowNetworkId)
      blockFlowApiKey                 <- validateApiKey(config.blockFlowApiKey)
      syncPeriod                      <- validateSyncPeriod(config.explorerSyncPeriod)
      tokenSupplyServiceSyncPeriod    <- validateSyncPeriod(config.tokenSupplyServiceSyncPeriod)
      hashRateServiceSyncPeriod       <- validateSyncPeriod(config.hashRateServiceSyncPeriod)
      finalizerServiceSyncPeriod      <- validateSyncPeriod(config.finalizerServiceSyncPeriod)
      transactionHistoryServicePeriod <- validateSyncPeriod(config.transactionHistoryServicePeriod)
    } yield
      ExplorerConfig(
        groupNum                        = groupNum,
        directCliqueAccess              = directCliqueAccess,
        blockFlowUri                    = blockFlowUri,
        networkId                       = networkId,
        maybeBlockFlowApiKey            = blockFlowApiKey,
        host                            = host,
        port                            = port,
        readOnly                        = readOnly,
        syncPeriod                      = syncPeriod,
        tokenSupplyServiceSyncPeriod    = tokenSupplyServiceSyncPeriod,
        hashRateServiceSyncPeriod       = hashRateServiceSyncPeriod,
        finalizerServiceSyncPeriod      = finalizerServiceSyncPeriod,
        transactionHistoryServicePeriod = transactionHistoryServicePeriod
      )

  def validateGroupNum(groupNum: Int): Try[Int] =
    if (groupNum < 0) {
      Failure(InvalidGroupNumber(groupNum))
    } else {
      //Is 0 a valid groupNum? Is there a max limit?
      Success(groupNum)
    }

  def validatePort(port: Int): Try[Int] =
    if (port <= 0 || port > 65535) {
      Failure(InvalidPortNumber(port))
    } else {
      Success(port)
    }

  def validateHost(host: String): Try[String] =
    Try(InetAddress.getByName(host))
      .map(_ => host)
      .recoverWith { throwable =>
        Failure(InvalidHost(host, throwable))
      }

  def validateUri(blockFlowHost: String, blockFlowPort: Int): Try[Uri] =
    for {
      host <- validateHost(blockFlowHost)
      port <- validatePort(blockFlowPort)
    } yield Uri(s"http://$host:$port")

  def validateNetworkId(networkId: Int): Try[NetworkId] =
    NetworkId.from(networkId) match {
      case Some(networkId) =>
        Success(networkId)

      case None =>
        Failure(InvalidNetworkId(networkId))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def validateApiKey(apiKey: Option[String]): Try[Option[ApiKey]] =
    apiKey match {
      case Some(apiKey) =>
        validateApiKey(apiKey).map(Some(_))

      case None =>
        Success(None)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def validateApiKey(apiKey: String): Try[ApiKey] =
    ApiKey.from(apiKey) match {
      case Left(err) =>
        Failure(InvalidApiKey(err))

      case Right(value) =>
        Success(value)
    }

  def validateSyncPeriod(interval: FiniteDuration): Try[FiniteDuration] =
    if (interval.fromNow.isOverdue()) {
      Failure(InvalidSyncPeriod(interval))
    } else {
      Success(interval)
    }
}

/**
  * Configurations to boot-up Explorer.
  *
  * The default constructor is private to ensure the configurations are
  * always valid.
  * */
final case class ExplorerConfig private (groupNum: Int,
                                         directCliqueAccess: Boolean,
                                         blockFlowUri: Uri,
                                         networkId: NetworkId,
                                         maybeBlockFlowApiKey: Option[ApiKey],
                                         host: String,
                                         port: Int,
                                         readOnly: Boolean,
                                         syncPeriod: FiniteDuration,
                                         tokenSupplyServiceSyncPeriod: FiniteDuration,
                                         hashRateServiceSyncPeriod: FiniteDuration,
                                         finalizerServiceSyncPeriod: FiniteDuration,
                                         transactionHistoryServicePeriod: FiniteDuration)
