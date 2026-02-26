// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.ChartsEndpoints
import org.alephium.explorer.api.model.{ActiveAddressesCount, ExportType, IntervalType, TimedCount}
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.service.{
  ActiveAddressHistoryService,
  HashrateService,
  TransactionHistoryService
}
import org.alephium.util.TimeStamp

class ChartsServer(
    maxTimeInterval: ExplorerConfig.MaxTimeInterval
)(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with ChartsEndpoints {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getHashrates.serverLogic[Future] { case (timeInterval, interval) =>
        validateTimeInterval(timeInterval, interval) {
          HashrateService.get(timeInterval.from, timeInterval.to, interval)
        }
      }),
      route(getAllChainsTxCount.serverLogic[Future] { case (timeInterval, interval) =>
        validateTimeInterval(timeInterval, interval) {
          TransactionHistoryService
            .getAllChains(timeInterval.from, timeInterval.to, interval)
            .map { seq =>
              seq.map { case (timestamp, count) =>
                TimedCount(timestamp, count)
              }
            }
        }
      }),
      route(getPerChainTxCount.serverLogic[Future] { case (timeInterval, interval) =>
        validateTimeInterval(timeInterval, interval) {
          TransactionHistoryService
            .getPerChain(timeInterval.from, timeInterval.to, interval)
        }
      }),
      route(getActiveAddresses.serverLogic[Future] { case (timeInterval, interval, exportType) =>
        validateTimeInterval(timeInterval, interval) {
          ActiveAddressHistoryService
            .getActiveAddresses(timeInterval.from, timeInterval.to, interval)
            .map { data =>
              exportType match {
                case None | Some(ExportType.JSON) =>
                  ActiveAddressesCount.Json(data.map { case (timestamp, count) =>
                    TimedCount(timestamp, count)
                  })
                case Some(ExportType.CSV) =>
                  ActiveAddressesCount.Csv(toCsv(data))
              }
            }
        }
      })
    )

  def toCsv(data: ArraySeq[(TimeStamp, Long)]): String = {
    val header = "timestamp,count"
    val rows   = data.map { case (timestamp, count) => s"${timestamp.millis},${count}" }
    (header +: rows).mkString("\n")
  }

  private def validateTimeInterval[A](timeInterval: TimeInterval, intervalType: IntervalType)(
      contd: => Future[A]
  ): Future[Either[ApiError[_ <: StatusCode], A]] =
    IntervalType.validateTimeInterval(
      timeInterval,
      intervalType,
      maxTimeInterval.hourly,
      maxTimeInterval.daily,
      maxTimeInterval.weekly,
      maxTimeInterval.monthly
    )(contd)

}
