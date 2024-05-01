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
import java.time.LocalTime

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import com.typesafe.config.{Config, ConfigUtil}
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus.{finiteDurationReader => _, _}
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import sttp.model.Uri

import org.alephium.api.model.ApiKey
import org.alephium.conf._
import org.alephium.explorer.error.ExplorerError._
import org.alephium.protocol.model.NetworkId
import org.alephium.util

@SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
object ExplorerConfig {

  def validateGroupNum(groupNum: Int): Try[Int] =
    if (groupNum < 0) {
      Failure(InvalidGroupNumber(groupNum))
    } else {
      // Is 0 a valid groupNum? Is there a max limit?
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

  implicit val locaTimeReader: ValueReader[LocalTime] =
    ValueReader[String](Ficus.stringValueReader).map { input =>
      LocalTime.parse(input)
    }

  implicit val bootUpMode: ValueReader[BootMode] =
    ValueReader[String](Ficus.stringValueReader).map { input =>
      BootMode.validate(input).get
    }

  implicit def listMapValueReader[A](implicit
      entryReader: ValueReader[A]
  ): ValueReader[ListMap[String, A]] =
    new ValueReader[ListMap[String, A]] {
      def read(config: Config, path: String): ListMap[String, A] = {
        val relativeConfig = config.getConfig(path)
        ListMap.from(relativeConfig.root().entrySet().asScala map { entry =>
          val key = entry.getKey
          key -> entryReader.read(relativeConfig, ConfigUtil.quoteString(key))
        })
      }
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
          explorer.services,
          explorer.cacheRowCountReloadPeriod,
          explorer.cacheBlockTimesReloadPeriod,
          explorer.cacheLatestBlocksReloadPeriod,
          explorer.exportTxsNumberThreshold,
          explorer.streamParallelism,
          explorer.maxTimeIntervals,
          explorer.market
        )
      }).get
    }

  def load(config: Config): ExplorerConfig =
    config.as[ExplorerConfig]("alephium")

  final private case class BlockFlow(
      groupNum: Int,
      directCliqueAccess: Boolean,
      host: String,
      port: Int,
      networkId: NetworkId,
      apiKey: Option[ApiKey]
  )

  final case class MaxTimeInterval(
      hourly: util.Duration,
      daily: util.Duration,
      weekly: util.Duration
  )

  final case class MaxTimeIntervals(
      amountHistory: MaxTimeInterval,
      charts: MaxTimeInterval,
      exportTxs: util.Duration
  )

  final case class Market(
      symbolName: ListMap[String, String],
      currencies: ArraySeq[String],
      coingeckoUri: String,
      marketChartDays: Int
  )

  final case class Services(
      blockflowSync: Services.BlockflowSync,
      mempoolSync: Services.MempoolSync,
      tokenSupply: Services.TokenSupply,
      hashrate: Services.Hashrate,
      txHistory: Services.TxHistory,
      finalizer: Services.Finalizer
  )

  object Services {
    final case class BlockflowSync(
        syncPeriod: FiniteDuration
    )

    final case class MempoolSync(
        enable: Boolean,
        syncPeriod: FiniteDuration
    )

    final case class TokenSupply(
        enable: Boolean,
        scheduleTime: LocalTime
    )

    final case class Hashrate(
        enable: Boolean,
        syncPeriod: FiniteDuration
    )

    final case class TxHistory(
        enable: Boolean,
        syncPeriod: FiniteDuration
    )

    final case class Finalizer(
        syncPeriod: FiniteDuration
    )
  }

  final private case class Explorer(
      host: String,
      port: Int,
      bootMode: BootMode,
      services: Services,
      cacheRowCountReloadPeriod: FiniteDuration,
      cacheBlockTimesReloadPeriod: FiniteDuration,
      cacheLatestBlocksReloadPeriod: FiniteDuration,
      exportTxsNumberThreshold: Int,
      streamParallelism: Int,
      maxTimeIntervals: MaxTimeIntervals,
      market: Market
  )

}

/** Configurations to boot-up Explorer.
  *
  * The default constructor is private to ensure the configurations are always valid.
  */
final case class ExplorerConfig private (
    groupNum: Int,
    directCliqueAccess: Boolean,
    blockFlowUri: Uri,
    networkId: NetworkId,
    maybeBlockFlowApiKey: Option[ApiKey],
    host: String,
    port: Int,
    bootMode: BootMode,
    services: ExplorerConfig.Services,
    cacheRowCountReloadPeriod: FiniteDuration,
    cacheBlockTimesReloadPeriod: FiniteDuration,
    cacheLatestBlocksReloadPeriod: FiniteDuration,
    exportTxsNumberThreshold: Int,
    streamParallelism: Int,
    maxTimeInterval: ExplorerConfig.MaxTimeIntervals,
    market: ExplorerConfig.Market
)
