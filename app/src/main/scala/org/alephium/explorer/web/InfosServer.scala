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

import org.alephium.explorer.BuildInfo
import org.alephium.explorer.api.InfosEndpoints
import org.alephium.explorer.api.model.{ExplorerInfo, PerChainValue}
import org.alephium.explorer.service.{BlockService, TransactionService, TokenSupplyService}
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, U256}

class InfosServer(val blockflowFetchMaxAge: Duration,
                  tokenSupplyService: TokenSupplyService,
                  blockService: BlockService,
                  transactionService: TransactionService)(implicit executionContext: ExecutionContext)
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
      toRoute(getHeights)(_ => blockService.listMaxHeights().map(Right(_)))~
      toRoute(getTransactionsNumber)(_ => transactionService.getTotalNumber().map{ res =>
        Right(res.map { case (chainFrom, chainTo, value) => PerChainValue(chainFrom, chainTo, value)})
      })


  private def toALPH(u256: U256): BigDecimal =
    new BigDecimal(u256.v).divide(new BigDecimal(ALPH.oneAlph.v))
}
