// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.annotation.tailrec
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
import org.alephium.util.TimeStamp

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
        findFirstMainChainEntity(entities.sortBy(_.creationTimestamp)(TimeStamp.ordering.reverse))
          .map {
            case Some(entity) => Right(entity.toApi)
            case None =>
              Left(
                ApiError.NotFound(
                  s"Contract not found in main chain: ${contract.toBase58}"
                )
              )
          }
      }
    }
  }

  private def findFirstMainChainEntity(entities: ArraySeq[ContractEntity])(implicit
      executionContext: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[ContractEntity]] = {

    @tailrec
    def find(
        remainingEntities: ArraySeq[ContractEntity],
        acc: Future[Option[ContractEntity]]
    ): Future[Option[ContractEntity]] =
      remainingEntities.headOption match {
        case Some(entity) =>
          val result = acc.flatMap {
            case None =>
              run(getMainChain(entity.creationBlockHash)).map {
                case Some(true) => Some(entity)
                case _          => None
              }
            case Some(_) => acc
          }
          find(remainingEntities.drop(1), result)
        case None => acc
      }

    find(entities, Future.successful(None))
  }
}
