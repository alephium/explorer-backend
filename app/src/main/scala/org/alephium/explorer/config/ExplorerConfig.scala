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

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus.{finiteDurationReader => _, _}
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import sttp.model.Uri

import org.alephium.api.model.ApiKey
import org.alephium.conf._
import org.alephium.explorer.error.ExplorerError._
import org.alephium.protocol.model.NetworkId

@SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
object ExplorerConfig {

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
    } yield Uri(host, port)

  def validateNetworkId(networkId: Int): Try[NetworkId] =
    NetworkId.from(networkId) match {
      case Some(networkId) =>
        Success(networkId)
      case None =>
        Failure(InvalidNetworkId(networkId))
    }

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

  implicit val networkIdReader: ValueReader[NetworkId] =
    ValueReader[Int].map { id =>
      validateNetworkId(id).get
    }

  implicit val apiKeyReader: ValueReader[ApiKey] =
    ValueReader[String].map { input =>
      validateApiKey(input).get
    }

  implicit val validateFiniteDuration: ValueReader[FiniteDuration] =
    ValueReader[FiniteDuration](Ficus.finiteDurationReader).map { input =>
      validateSyncPeriod(input).get
    }

  implicit val bootUpMode: ValueReader[BootMode] =
    ValueReader[String](Ficus.stringValueReader).map { input =>
      BootMode.validate(input).get
    }

  implicit val explorerConfigReader: ValueReader[ExplorerConfig] =
    valueReader { implicit cfg =>
      val explorer  = as[Explorer]("explorer")
      val blockflow = as[BlockFlow]("blockflow")

      (for {
        groupNum     <- validateGroupNum(blockflow.groupNum)
        blockflowUri <- validateUri(blockflow.host, blockflow.port)
        host         <- validateHost(explorer.host)
        port         <- validatePort(explorer.port)
      } yield {
        ExplorerConfig(
          groupNum,
          blockflow.directCliqueAccess,
          blockflowUri,
          blockflow.networkId,
          blockflow.apiKey,
          host,
          port,
          explorer.bootMode,
          explorer.syncPeriod,
          explorer.tokenSupplyServiceSyncPeriod,
          explorer.hashRateServiceSyncPeriod,
          explorer.finalizerServiceSyncPeriod,
          explorer.transactionHistoryServiceSyncPeriod,
          explorer.cacheRowCountReloadPeriod,
          explorer.cacheBlockTimesReloadPeriod,
          explorer.cacheLatestBlocksReloadPeriod
        )
      }).get
    }

  def load(config: Config): ExplorerConfig =
    config.as[ExplorerConfig]("alephium")

  private final case class BlockFlow(groupNum: Int,
                                     directCliqueAccess: Boolean,
                                     host: String,
                                     port: Int,
                                     networkId: NetworkId,
                                     apiKey: Option[ApiKey])

  private final case class Explorer(host: String,
                                    port: Int,
                                    bootMode: BootMode,
                                    syncPeriod: FiniteDuration,
                                    tokenSupplyServiceSyncPeriod: FiniteDuration,
                                    hashRateServiceSyncPeriod: FiniteDuration,
                                    finalizerServiceSyncPeriod: FiniteDuration,
                                    transactionHistoryServiceSyncPeriod: FiniteDuration,
                                    cacheRowCountReloadPeriod: FiniteDuration,
                                    cacheBlockTimesReloadPeriod: FiniteDuration,
                                    cacheLatestBlocksReloadPeriod: FiniteDuration)
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
                                         bootMode: BootMode,
                                         syncPeriod: FiniteDuration,
                                         tokenSupplyServiceSyncPeriod: FiniteDuration,
                                         hashRateServiceSyncPeriod: FiniteDuration,
                                         finalizerServiceSyncPeriod: FiniteDuration,
                                         transactionHistoryServiceSyncPeriod: FiniteDuration,
                                         cacheRowCountReloadPeriod: FiniteDuration,
                                         cacheBlockTimesReloadPeriod: FiniteDuration,
                                         cacheLatestBlocksReloadPeriod: FiniteDuration)
