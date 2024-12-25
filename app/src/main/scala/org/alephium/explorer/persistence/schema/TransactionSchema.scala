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

package org.alephium.explorer.persistence.schema

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TransactionEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object TransactionSchema extends SchemaMainChain[TransactionEntity]("transactions") {

  class Transactions(tag: Tag) extends Table[TransactionEntity](tag, name) {
    def hash: Rep[TransactionId]       = column[TransactionId]("hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash]      = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]      = column[TimeStamp]("block_timestamp")
    def chainFrom: Rep[GroupIndex]     = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]       = column[GroupIndex]("chain_to")
    def version: Rep[Byte]             = column[Byte]("version")
    def networkId: Rep[Byte]           = column[Byte]("network_id")
    def scriptOpt: Rep[Option[String]] = column[Option[String]]("script_opt")
    def gasAmount: Rep[Int]            = column[Int]("gas_amount")
    def gasPrice: Rep[U256] =
      column[U256]("gas_price", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def txOrder: Rep[Int]               = column[Int]("tx_order")
    def mainChain: Rep[Boolean]         = column[Boolean]("main_chain")
    def scriptExecutionOk: Rep[Boolean] = column[Boolean]("script_execution_ok")
    def inputSignatures: Rep[Option[ArraySeq[ByteString]]] =
      column[Option[ArraySeq[ByteString]]]("input_signatures")
    def scriptSignatures: Rep[Option[ArraySeq[ByteString]]] =
      column[Option[ArraySeq[ByteString]]]("script_signatures")
    def coinbase: Rep[Boolean] = column[Boolean]("coinbase")

    def pk: PrimaryKey = primaryKey("txs_pk", (hash, blockHash))

    def timestampIdx: Index = index("txs_timestamp_idx", timestamp)
    def blockHashIdx: Index = index("txs_block_hash_idx", blockHash)
    def chainFromIdx: Index = index("txs_chain_from_idx", chainFrom)
    def chainToIdx: Index   = index("txs_chain_to_idx", chainTo)

    def * : ProvenShape[TransactionEntity] =
      (
        hash,
        blockHash,
        timestamp,
        chainFrom,
        chainTo,
        version,
        networkId,
        scriptOpt,
        gasAmount,
        gasPrice,
        txOrder,
        mainChain,
        scriptExecutionOk,
        inputSignatures,
        scriptSignatures,
        coinbase
      )
        .<>((TransactionEntity.apply _).tupled, TransactionEntity.unapply)
  }

  val table: TableQuery[Transactions] = TableQuery[Transactions]
}
