// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.{GrouplessAddress, TransactionPerAddressEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object TransactionPerAddressSchema
    extends SchemaMainChain[TransactionPerAddressEntity]("transaction_per_addresses") {

  class TransactionPerAddresses(tag: Tag) extends Table[TransactionPerAddressEntity](tag, name) {
    def address: Rep[Address] = column[Address]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def txOrder: Rep[Int]          = column[Int]("tx_order")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def coinbase: Rep[Boolean]     = column[Boolean]("coinbase")

    def pk: PrimaryKey = primaryKey("txs_per_address_pk", (txHash, blockHash, address))

    def hashIdx: Index      = index("txs_per_address_tx_hash_idx", txHash)
    def blockHashIdx: Index = index("txs_per_address_block_hash_idx", blockHash)
    def addressIdx: Index   = index("txs_per_address_address_idx", address)
    def addressTimestampIdx: Index =
      index("txs_per_address_address_timestamp_idx", (address, timestamp))

    def * : ProvenShape[TransactionPerAddressEntity] =
      (address, grouplessAddress, txHash, blockHash, timestamp, txOrder, mainChain, coinbase)
        .<>((TransactionPerAddressEntity.apply _).tupled, TransactionPerAddressEntity.unapply)
  }

  def createIndexes(): DBIO[Unit] =
    DBIO.seq(
      createMainChainIndex(),
      CommonIndex.blockTimestampTxOrderIndex(this),
      CommonIndex.timestampIndex(this)
    )

  def createConcurrentIndexes()(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      _ <- createGrouplessAddressTimestampIndex()
      _ <- createUniqueGrouplessIndex()
      _ <- createCountingGrouplessIndex()
    } yield ()

  // TODO: This index might not be necessary anymore with the new `txs_per_address_uniq_groupless_idx` index
  def createGrouplessAddressTimestampIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS txs_per_address_groupless_address_timestamp_idx
      ON transaction_per_addresses (groupless_address, block_timestamp)
      WHERE groupless_address IS NOT NULL;
    """

  def createUniqueGrouplessIndex(): DBActionW[Int] =
    sqlu"""
        CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS txs_per_address_uniq_groupless_idx
        ON transaction_per_addresses (
          groupless_address, block_timestamp DESC, tx_order, tx_hash, block_hash, coinbase
        )
        WHERE main_chain = true AND groupless_address IS NOT NULL;
      """

  def createCountingGrouplessIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX CONCURRENTLY IF NOT EXISTS txs_per_address_groupless_counting_idx
      ON transaction_per_addresses (groupless_address, tx_hash)
      WHERE main_chain = true AND groupless_address IS NOT NULL;
  """

  val table: TableQuery[TransactionPerAddresses] = TableQuery[TransactionPerAddresses]
}
