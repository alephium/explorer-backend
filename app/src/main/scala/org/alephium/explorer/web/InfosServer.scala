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

package org.alephium.explorer.web

import java.math.BigDecimal

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.{BuildInfo, GroupSetting}
import org.alephium.explorer.api.InfosEndpoints
import org.alephium.explorer.api.model.ExplorerInfo
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.service.{BlockService, TokenSupplyService, TransactionService}
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, U256}

class InfosServer(val blockflowFetchMaxAge: Duration,
                  tokenSupplyService: TokenSupplyService,
                  blockService: BlockService,
                  transactionService: TransactionService)(
    implicit executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockCache: BlockCache,
    transactionCache: TransactionCache,
    groupSettings: GroupSetting)
    extends Server
    with InfosEndpoints {

  val route: Route =
    toRoute(getInfos) { _ =>
      Future.successful(Right(ExplorerInfo(BuildInfo.releaseVersion, BuildInfo.commitId)))
    } ~
      toRoute(listTokenSupply) { pagination =>
        tokenSupplyService.listTokenSupply(pagination).map(Right(_))
      } ~
      toRoute(getCirculatingSupply) { _ =>
        tokenSupplyService
          .getLatestTokenSupply()
          .map { supply =>
            val circulating = supply.map(_.circulating).getOrElse(U256.Zero)
            Right(toALPH(circulating))
          }
      } ~
      toRoute(getTotalSupply) { _ =>
        tokenSupplyService
          .getLatestTokenSupply()
          .map { supply =>
            val total = supply.map(_.total).getOrElse(U256.Zero)
            Right(toALPH(total))
          }
      } ~
      toRoute(getReservedSupply) { _ =>
        tokenSupplyService
          .getLatestTokenSupply()
          .map { supply =>
            val reserved = supply.map(_.reserved).getOrElse(U256.Zero)
            Right(toALPH(reserved))
          }
      } ~
      toRoute(getLockedSupply) { _ =>
        tokenSupplyService
          .getLatestTokenSupply()
          .map { supply =>
            val locked = supply.map(_.locked).getOrElse(U256.Zero)
            Right(toALPH(locked))
          }
      } ~
      toRoute(getHeights)(_           => blockService.listMaxHeights().map(Right(_))) ~
      toRoute(getTotalTransactions)(_ => Future(Right(transactionService.getTotalNumber()))) ~
      toRoute(getAverageBlockTime)(_  => blockService.getAverageBlockTime().map(Right(_)))

  private def toALPH(u256: U256): BigDecimal =
    new BigDecimal(u256.v).divide(new BigDecimal(ALPH.oneAlph.v))
}
