// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.{Token}
import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.{GrouplessAddress, OutputEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object OutputSchema extends SchemaMainChain[OutputEntity]("outputs") {

  class Outputs(tag: Tag) extends Table[OutputEntity](tag, name) {
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def outputType: Rep[OutputEntity.OutputType] = column[OutputEntity.OutputType]("output_type")
    def hint: Rep[Int]                           = column[Int]("hint")
    def key: Rep[Hash]                           = column[Hash]("key", O.SqlType("BYTEA"))
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def address: Rep[Address] = column[Address]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def tokens: Rep[Option[ArraySeq[Token]]] = column[Option[ArraySeq[Token]]]("tokens")
    def mainChain: Rep[Boolean]              = column[Boolean]("main_chain")
    def conflicted: Rep[Option[Boolean]]     = column[Option[Boolean]]("conflicted")
    def lockTime: Rep[Option[TimeStamp]]     = column[Option[TimeStamp]]("lock_time")
    def message: Rep[Option[ByteString]]     = column[Option[ByteString]]("message")
    def outputOrder: Rep[Int]                = column[Int]("output_order")
    def txOrder: Rep[Int]                    = column[Int]("tx_order")
    def coinbase: Rep[Boolean]               = column[Boolean]("coinbase")
    def spentFinalized: Rep[Option[TransactionId]] =
      column[Option[TransactionId]]("spent_finalized", O.Default(None))
    def spentTimestamp: Rep[Option[TimeStamp]] = column[Option[TimeStamp]]("spent_timestamp")
    def fixedOutput: Rep[Boolean]              = column[Boolean]("fixed_output")

    def pk: PrimaryKey = primaryKey("outputs_pk", (key, blockHash))

    def blockHashIdx: Index      = index("outputs_block_hash_idx", blockHash)
    def addressIdx: Index        = index("outputs_address_idx", address)
    def timestampIdx: Index      = index("outputs_timestamp_idx", timestamp)
    def spentTimestampIdx: Index = index("outputs_spent_timestamp_idx", spentTimestamp)
    def outputsBlockHashTxHashIdx: Index =
      index("outputs_tx_hash_block_hash_idx", (txHash, blockHash))

    def * : ProvenShape[OutputEntity] =
      (
        blockHash,
        txHash,
        timestamp,
        outputType,
        hint,
        key,
        amount,
        address,
        grouplessAddress,
        tokens,
        mainChain,
        conflicted,
        lockTime,
        message,
        outputOrder,
        txOrder,
        coinbase,
        spentFinalized,
        spentTimestamp,
        fixedOutput
      )
        .<>((OutputEntity.apply _).tupled, OutputEntity.unapply)
  }

  def createNonSpentIndex(): DBActionW[Int] =
    sqlu"create unique index if not exists non_spent_output_idx on #${name} (address, main_chain, key, block_hash) where spent_finalized IS NULL;"

  def createConcurrentIndexes()(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      _ <- createNonSpentGrouplessIndex()
      _ <- createSpentOutputCoveringIndex()
      _ <- createNonSpentOutputGrouplessCoveringIndex()
    } yield ()

  def createNonSpentGrouplessIndex(): DBActionW[Int] =
    sqlu"""
      CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS non_spent_output_groupless_idx
      ON #${name} (groupless_address, main_chain, key, block_hash)
      WHERE spent_finalized IS NULL
      AND groupless_address IS NOT NULL;
    """

  def createSpentOutputCoveringIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS non_spent_output_covering_include_idx
      ON #${name} (address, main_chain, spent_finalized, key)
      INCLUDE (amount, lock_time)
      WHERE spent_finalized IS NULL AND main_chain = true;
    """

  def createNonSpentOutputGrouplessCoveringIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS non_spent_output_groupless_covering_include_idx
      ON outputs (groupless_address, main_chain, spent_finalized, key)
      INCLUDE (amount, lock_time)
      WHERE spent_finalized IS NULL AND groupless_address IS NOT NULL AND main_chain = true;
    """

  val table: TableQuery[Outputs] = TableQuery[Outputs]
}
