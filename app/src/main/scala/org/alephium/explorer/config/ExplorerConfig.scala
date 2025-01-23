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

import java.io.File
import java.net.InetAddress
import java.nio.file.Path
import java.time.LocalTime

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigUtil}
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus.{finiteDurationReader => _, _}
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import sttp.model.Uri

import org.alephium.api.model.ApiKey
import org.alephium.conf._
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.util.FileUtil
import org.alephium.protocol.model.NetworkId
import org.alephium.util

@SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
object ExplorerConfig {

  def getConfigFile(rootPath: Path, name: String): File =
    rootPath.resolve(s"$name.conf").toFile

  def getUserConfig(rootPath: Path): File = {
    val file = getConfigFile(rootPath, "user")
    FileUtil.createFileIfNotExists(file)
    file
  }

  def loadConfig(rootPath: Path): Try[Config] = {
    for {
      userConfig <- parseConfigFile(getUserConfig(rootPath))
    } yield {
      val defaultConfig = ConfigFactory.parseResources("application.conf")
      ConfigFactory.load(userConfig.withFallback(defaultConfig))
    }
  }

  def parseConfigFile(file: File): Try[Config] =
    try {
      if (file.exists()) {
        Success(ConfigFactory.parseFile(file))
      } else {
        Success(ConfigFactory.empty())
      }
    } catch {
      case e: ConfigException =>
        Failure(ConfigParsingError(file, e))
    }

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

  def validateScheme(scheme: String): Try[String] =
    if (scheme == "http" || scheme == "https") {
      Success(scheme)
    } else {
      Failure(InvalidScheme(scheme))
    }

  def validateUri(scheme: String, host: String, port: Int): Try[Uri] =
    for {
      scheme <- validateScheme(scheme)
      host   <- validateHost(host)
      port   <- validatePort(port)
      uri <- Uri
        .safeApply(scheme, host, port)
        .left
        .map(err => InvalidUri(scheme, host, port, err))
        .toTry
    } yield uri

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
        blockflowUri <- validateUri(blockflow.scheme, blockflow.host, blockflow.port)
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
          explorer.holderServiceScheduleTime,
          explorer.tokenSupplyServiceScheduleTime,
          explorer.hashRateServiceSyncPeriod,
          explorer.finalizerServiceSyncPeriod,
          explorer.transactionHistoryServiceSyncPeriod,
          explorer.cacheRowCountReloadPeriod,
          explorer.cacheBlockTimesReloadPeriod,
          explorer.cacheLatestBlocksReloadPeriod,
          explorer.cacheMetricsReloadPeriod,
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
      scheme: String,
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
      chartSymbolName: ListMap[String, String],
      currencies: ArraySeq[String],
      liquidityMinimum: Double,
      mobulaUri: String,
      coingeckoUri: String,
      tokenListUri: String,
      marketChartDays: Int
  )

  final private case class Explorer(
      host: String,
      port: Int,
      bootMode: BootMode,
      syncPeriod: FiniteDuration,
      holderServiceScheduleTime: LocalTime,
      tokenSupplyServiceScheduleTime: LocalTime,
      hashRateServiceSyncPeriod: FiniteDuration,
      finalizerServiceSyncPeriod: FiniteDuration,
      transactionHistoryServiceSyncPeriod: FiniteDuration,
      cacheRowCountReloadPeriod: FiniteDuration,
      cacheBlockTimesReloadPeriod: FiniteDuration,
      cacheLatestBlocksReloadPeriod: FiniteDuration,
      cacheMetricsReloadPeriod: FiniteDuration,
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
@scala.annotation.nowarn
final case class ExplorerConfig private (
    groupNum: Int,
    directCliqueAccess: Boolean,
    blockFlowUri: Uri,
    networkId: NetworkId,
    maybeBlockFlowApiKey: Option[ApiKey],
    host: String,
    port: Int,
    bootMode: BootMode,
    syncPeriod: FiniteDuration,
    holderServiceScheduleTime: LocalTime,
    tokenSupplyServiceScheduleTime: LocalTime,
    hashRateServiceSyncPeriod: FiniteDuration,
    finalizerServiceSyncPeriod: FiniteDuration,
    transactionHistoryServiceSyncPeriod: FiniteDuration,
    cacheRowCountReloadPeriod: FiniteDuration,
    cacheBlockTimesReloadPeriod: FiniteDuration,
    cacheLatestBlocksReloadPeriod: FiniteDuration,
    cacheMetricsReloadPeriod: FiniteDuration,
    exportTxsNumberThreshold: Int,
    streamParallelism: Int,
    maxTimeInterval: ExplorerConfig.MaxTimeIntervals,
    market: ExplorerConfig.Market
)
