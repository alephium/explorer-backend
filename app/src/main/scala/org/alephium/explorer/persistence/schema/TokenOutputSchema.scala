// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import scala.concurrent.ExecutionContext

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.{GrouplessAddress, OutputEntity, TokenOutputEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object TokenOutputSchema extends SchemaMainChain[TokenOutputEntity]("token_outputs") {

  class TokenOutputs(tag: Tag) extends Table[TokenOutputEntity](tag, name) {
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def outputType: Rep[OutputEntity.OutputType] = column[OutputEntity.OutputType]("output_type")
    def hint: Rep[Int]                           = column[Int]("hint")
    def key: Rep[Hash]                           = column[Hash]("key", O.SqlType("BYTEA"))
    def token: Rep[TokenId]                      = column[TokenId]("token")
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def address: Rep[Address] = column[Address]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def mainChain: Rep[Boolean]          = column[Boolean]("main_chain")
    def lockTime: Rep[Option[TimeStamp]] = column[Option[TimeStamp]]("lock_time")
    def message: Rep[Option[ByteString]] = column[Option[ByteString]]("message")
    def outputOrder: Rep[Int]            = column[Int]("output_order")
    def txOrder: Rep[Int]                = column[Int]("tx_order")
    def spentFinalized: Rep[Option[TransactionId]] =
      column[Option[TransactionId]]("spent_finalized", O.Default(None))
    def spentTimestamp: Rep[Option[TimeStamp]] = column[Option[TimeStamp]]("spent_timestamp")

    def pk: PrimaryKey = primaryKey("token_outputs_pk", (key, token, blockHash))

    def keyIdx: Index            = index("token_outputs_key_idx", key)
    def blockHashIdx: Index      = index("token_outputs_block_hash_idx", blockHash)
    def txHashIdx: Index         = index("token_outputs_tx_hash_idx", txHash)
    def addressIdx: Index        = index("token_outputs_address_idx", address)
    def timestampIdx: Index      = index("token_outputs_timestamp_idx", timestamp)
    def tokenIdx: Index          = index("token_outputs_token_idx", token)
    def spentTimestampIdx: Index = index("token_outputs_spent_timestamp_idx", spentTimestamp)

    def * : ProvenShape[TokenOutputEntity] =
      (
        blockHash,
        txHash,
        timestamp,
        outputType,
        hint,
        key,
        token,
        amount,
        address,
        grouplessAddress,
        mainChain,
        lockTime,
        message,
        outputOrder,
        txOrder,
        spentFinalized,
        spentTimestamp
      )
        .<>((TokenOutputEntity.apply _).tupled, TokenOutputEntity.unapply)
  }

  def createConcurrentIndexes()(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      _ <- createNonSpentIndex()
      _ <- createNonSpentGrouplessIndex()
    } yield ()

  def createNonSpentIndex(): DBActionW[Int] =
    sqlu"""
      CREATE UNIQUE INDEX IF NOT EXISTS token_non_spent_output_idx
      ON #${name} (token, address, main_chain, key, block_hash)
      WHERE spent_finalized IS NULL;
    """

  def createNonSpentGrouplessIndex(): DBActionW[Int] =
    sqlu"""
      CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS token_non_spent_output_groupless_idx
      ON #${name} (token, groupless_address, main_chain, key, block_hash)
      WHERE spent_finalized IS NULL
      AND groupless_address IS NOT NULL;
    """

  val table: TableQuery[TokenOutputs] = TableQuery[TokenOutputs]
}
