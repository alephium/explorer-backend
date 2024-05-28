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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.api.ContractsEndpoints
import org.alephium.explorer.api.model.{ContractLiveness, ContractParent, SubContracts}
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.ContractEntity
import org.alephium.explorer.persistence.queries.BlockQueries.getMainChain
import org.alephium.explorer.persistence.queries.ContractQueries._
import org.alephium.protocol.model.Address

class ContractServer(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with ContractsEndpoints {
  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getContractInfo.serverLogic[Future] { contract =>
        ContractServer.getLatestContractInfo(contract)
      }),
      route(getParentAddress.serverLogicSuccess[Future] { contract =>
        run(getParentAddressQuery(contract).map(ContractParent.apply))
      }),
      route(getSubContracts.serverLogicSuccess[Future] { case (contract, pagination) =>
        run(getSubContractsQuery(contract, pagination).map(SubContracts.apply))
      })
    )
}

object ContractServer {

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def getLatestContractInfo(contract: Address.Contract)(implicit
      executionContext: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Either[ApiError.NotFound, ContractLiveness]] = {
    run(getContractEntity(contract)).flatMap { entities =>
      if (entities.isEmpty) {
        Future.successful(
          Left(ApiError.NotFound(s"Contract not found: ${contract.toBase58}"))
        )
      } else if (entities.sizeIs == 1) {
        Future.successful(Right(entities.head.toApi))
      } else {
        // Find latest contract info
        collectMainChainContractEntities(entities)
          .map(_.maxByOption(_.creationTimestamp) match {
            case Some(entity) => Right(entity.toApi)
            case None =>
              Left(
                ApiError.NotFound(
                  s"Contract not found in main chain: ${contract.toBase58}"
                )
              )
          })
      }
    }
  }

  private def collectMainChainContractEntities(entities: ArraySeq[ContractEntity])(implicit
      executionContext: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[ContractEntity]] = {
    Future
      .sequence(
        entities.map { entity =>
          run(getMainChain(entity.creationBlockHash)).map {
            case Some(true) => Some(entity)
            case _          => None
          }
        }
      )
      .map(_.collect { case Some(entity) => entity })
  }
}
