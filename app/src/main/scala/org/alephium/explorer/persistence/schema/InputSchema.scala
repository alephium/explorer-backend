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
import org.alephium.explorer.persistence.model.{GrouplessAddress, InputEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object InputSchema extends SchemaMainChain[InputEntity]("inputs") {

  class Inputs(tag: Tag) extends Table[InputEntity](tag, name) {
    def blockHash: Rep[BlockHash]             = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[TransactionId]            = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]             = column[TimeStamp]("block_timestamp")
    def hint: Rep[Int]                        = column[Int]("hint")
    def outputRefKey: Rep[Hash]               = column[Hash]("output_ref_key", O.SqlType("BYTEA"))
    def unlockScript: Rep[Option[ByteString]] = column[Option[ByteString]]("unlock_script")
    def mainChain: Rep[Boolean]               = column[Boolean]("main_chain")
    def conflicted: Rep[Option[Boolean]]      = column[Option[Boolean]]("conflicted")
    def inputOrder: Rep[Int]                  = column[Int]("input_order")
    def txOrder: Rep[Int]                     = column[Int]("tx_order")
    def outputRefTxHash: Rep[Option[TransactionId]] =
      column[Option[TransactionId]]("output_ref_tx_hash")
    def outputRefAddress: Rep[Option[Address]] = column[Option[Address]]("output_ref_address")
    def outputRefGrouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("output_ref_groupless_address")
    def outputRefAmount: Rep[Option[U256]] =
      column[Option[U256]](
        "output_ref_amount",
        O.SqlType("DECIMAL(80,0)")
      ) // U256.MaxValue has 78 digits
    def outputRefTokens: Rep[Option[ArraySeq[Token]]] =
      column[Option[ArraySeq[Token]]]("output_ref_tokens")
    def contractInput: Rep[Boolean] = column[Boolean]("contract_input")

    def pk: PrimaryKey = primaryKey("inputs_pk", (outputRefKey, blockHash))

    def blockHashIdx: Index        = index("inputs_block_hash_idx", blockHash)
    def timestampIdx: Index        = index("inputs_timestamp_idx", timestamp)
    def outputRefAddressIdx: Index = index("inputs_output_ref_address_idx", outputRefAddress)
    def inputsBlockHashTxHashIdx: Index =
      index("inputs_tx_hash_block_hash_idx", (txHash, blockHash))

    def * : ProvenShape[InputEntity] =
      (
        blockHash,
        txHash,
        timestamp,
        hint,
        outputRefKey,
        unlockScript,
        mainChain,
        conflicted,
        inputOrder,
        txOrder,
        outputRefTxHash,
        outputRefAddress,
        outputRefGrouplessAddress,
        outputRefAmount,
        outputRefTokens,
        contractInput
      )
        .<>((InputEntity.apply _).tupled, InputEntity.unapply)
  }

  def createOutputRefAddressNullIndex(): DBActionW[Int] =
    sqlu"""CREATE INDEX IF NOT EXISTS inputs_output_ref_amount_null_idx
      ON #${name} (output_ref_tx_hash, output_ref_address, output_ref_amount, output_ref_tokens)
      WHERE output_ref_amount IS NULL"""

  def createConcurrentIndexes()(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      _ <- createOutupRefAddressMainChainTimestampIndex()
      _ <- createOutupRefGrouplessAddressMainChainTimestampIndex()
    } yield ()

  def createOutupRefAddressMainChainTimestampIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS inputs_ref_address_main_chain_timestamp_idx
      ON inputs (output_ref_address, main_chain, block_timestamp);
    """

  def createOutupRefGrouplessAddressMainChainTimestampIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS inputs_ref_groupless_address_main_chain_timestamp_idx
      ON inputs (output_ref_groupless_address, main_chain, block_timestamp)
      WHERE output_ref_groupless_address IS NOT NULL;
    """

  val table: TableQuery[Inputs] = TableQuery[Inputs]
}
