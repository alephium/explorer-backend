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
import scala.concurrent.duration._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.{BuildInfo, GroupSetting}
import org.alephium.explorer.api.InfosEndpoints
import org.alephium.explorer.api.model.{ExplorerInfo, TokenSupply}
import org.alephium.explorer.cache.{AsyncReloadingCache, BlockCache, TransactionCache}
import org.alephium.explorer.service.{BlockService, TokenSupplyService, TransactionService}
import org.alephium.protocol.ALPH
import org.alephium.util.U256

class InfosServer(tokenSupplyService: TokenSupplyService,
                  blockService: BlockService,
                  transactionService: TransactionService)(
    implicit val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockCache: BlockCache,
    transactionCache: TransactionCache,
    groupSettings: GroupSetting)
    extends Server
    with InfosEndpoints {

  // scalafmt is struggling on this one, maybe latest version wil work.
  // format: off
  val route: Route =
    toRoute(getInfos.serverLogicSuccess[Future] { _ =>
      Future.successful(ExplorerInfo(BuildInfo.releaseVersion, BuildInfo.commitId))
    }) ~
      toRoute(listTokenSupply.serverLogicSuccess[Future] { pagination =>
        tokenSupplyService.listTokenSupply(pagination)
      }) ~
      toRoute(getCirculatingSupply.serverLogicSuccess[Future] { _ =>
          getLatestTokenSupply()
          .map { supply =>
            val circulating = supply.map(_.circulating).getOrElse(U256.Zero)
            toALPH(circulating)
          }
      }) ~
      toRoute(getTotalSupply.serverLogicSuccess[Future] { _ =>
          getLatestTokenSupply()
          .map { supply =>
            val total = supply.map(_.total).getOrElse(U256.Zero)
            toALPH(total)
          }
      }) ~
      toRoute(getReservedSupply.serverLogicSuccess[Future] { _ =>
          getLatestTokenSupply()
          .map { supply =>
            val reserved = supply.map(_.reserved).getOrElse(U256.Zero)
            toALPH(reserved)
          }
      }) ~
      toRoute(getLockedSupply.serverLogicSuccess[Future] { _ =>
          getLatestTokenSupply()
          .map { supply =>
            val locked = supply.map(_.locked).getOrElse(U256.Zero)
            toALPH(locked)
          }
      }) ~
      toRoute(getHeights.serverLogicSuccess[Future]{ _ =>
         blockService.listMaxHeights()
      }) ~
      toRoute(getTotalTransactions.serverLogicSuccess[Future]{ _=>
        Future(transactionService.getTotalNumber())
      })~
      toRoute(getAverageBlockTime.serverLogicSuccess[Future]{ _=>
        blockService.getAverageBlockTime()
      })
  // format: on

  private def toALPH(u256: U256): BigDecimal =
    new BigDecimal(u256.v).divide(new BigDecimal(ALPH.oneAlph.v))

  private val latestTokenSupplyCache: AsyncReloadingCache[Option[TokenSupply]] =
    AsyncReloadingCache[Option[TokenSupply]](None, 1.minutes) { _ =>
      tokenSupplyService.getLatestTokenSupply()
    }

  private def getLatestTokenSupply(): Future[Option[TokenSupply]] =
    latestTokenSupplyCache.get() match {
      case Some(tokenSupply) => Future.successful(Some(tokenSupply))
      case None =>
        latestTokenSupplyCache.expireAndReloadFuture().map { _ =>
          latestTokenSupplyCache.get()
        }
    }
}
