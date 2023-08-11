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

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.{ContractEntity, EventEntity}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.Address

object ContractQueries {
  def insertOrUpdateContracts(
      events: Iterable[EventEntity]
  )(implicit ec: ExecutionContext): DBActionW[Unit] = {
    for {
      _ <- insertContractCreation(events)
      _ <- updateContractDestruction(events)
    } yield ()
  }

  def insertContractCreation(events: Iterable[EventEntity]): DBActionW[Int] = {
    insertContractCreationEventEntities(
      events.flatMap(ContractEntity.creationFromEventEntity)
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def updateContractDestruction(events: Iterable[EventEntity]): DBActionW[Int] = {
    updateContractDestructionEventEntities(
      events.flatMap(ContractEntity.destructionFromEventEntity)
    )
  }

  def insertContractCreationEventEntities(events: Iterable[ContractEntity]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = events, columnsPerRow = 7) { (events, placeholder) =>
      val query =
        s"""
           |INSERT INTO contracts
           |("contract", "parent", "std_interface_id_guessed", "creation_block_hash", "creation_tx_hash","creation_timestamp","creation_event_order")
           |VALUES $placeholder
           |ON CONFLICT
           | ON CONSTRAINT contracts_pk
           | DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          events foreach { event =>
            params >> event.contract
            params >> event.parent
            params >> event.stdInterfaceIdGuessed
            params >> event.creationBlockHash
            params >> event.creationTxHash
            params >> event.creationTimestamp
            params >> event.creationEventOrder
          }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).asUpdate
    }
  }

  private def updateContractDestructionEventEntities(
      destroyInfos: Iterable[ContractEntity.DestroyInfo]
  ): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = destroyInfos, columnsPerRow = 5) { (destroyInfos, _) =>
      val query =
        destroyInfos
          .map { _ =>
            s"""
           UPDATE contracts SET destruction_block_hash = ?, destruction_tx_hash = ?, destruction_timestamp = ?, destruction_event_order = ?
           WHERE contract = ?
           AND destruction_block_hash IS NULL
         """ // We update where destruction_block_hash is null, because a contract can be recreated after it was first destroyed.
          }
          .mkString("BEGIN;", ";", ";COMMIT;")

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          destroyInfos foreach { destroyInfo =>
            params >> destroyInfo.blockHash
            params >> destroyInfo.txHash
            params >> destroyInfo.timestamp
            params >> destroyInfo.eventOrder
            params >> destroyInfo.contract
          }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).asUpdate
    }
  }

  def getParentAddressQuery(
      contract: Address
  )(implicit ec: ExecutionContext): DBActionR[Option[Address]] = {
    sql"""
      SELECT parent
      FROM contracts
      WHERE contract = $contract
      LIMIT 1
      """.asASE[Option[Address]](optionAddressGetResult).headOrNone.map(_.flatten)
  }

  def getSubContractsQuery(parent: Address, pagination: Pagination): DBActionSR[Address] = {
    sql"""
      SELECT contract
      FROM contracts
      WHERE parent = $parent
      ORDER BY creation_timestamp DESC, creation_event_order
      """
      .paginate(pagination)
      .asASE[Address](addressGetResult)
  }

  def insertNFTCollectionMetadata(
      contract: Address,
      metadata: NFTCollectionMetadata
  ): DBActionW[Int] = {
    sqlu"""
      INSERT INTO nft_collection_metadata (
        "contract",
        "collection_uri",
        "total_supply"
        )
      VALUES (${contract},${metadata.collectionUri},${metadata.totalSupply})
      ON CONFLICT
      ON CONSTRAINT nft_collection_metadata_pkey
      DO NOTHING
    """
  }

  def listContractWithoutInterfaceIdQuery(): DBActionW[ArraySeq[Address.Contract]] = {
    sql"""
      SELECT contract
      FROM contracts
      WHERE interface_id IS NULL
      AND destruction_block_hash IS NULL
    """.asASE[Address.Contract](addressContractGetResult)
  }

  def updateContractInterfaceId(
      contract: Address.Contract,
      interfaceId: StdInterfaceId
  ): DBActionW[Int] = {
    sqlu"""
      UPDATE contracts
      SET interface_id = ${interfaceId.id}
      WHERE contract = $contract
      AND destruction_block_hash IS NULL
    """
  }
}
