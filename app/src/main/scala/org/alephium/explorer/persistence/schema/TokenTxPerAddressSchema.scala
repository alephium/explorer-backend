// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.{GrouplessAddress, TokenTxPerAddressEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

object TokenPerAddressSchema
    extends SchemaMainChain[TokenTxPerAddressEntity]("token_tx_per_addresses") {

  class TokenPerAddresses(tag: Tag) extends Table[TokenTxPerAddressEntity](tag, name) {
    def address: Rep[Address] = column[Address]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def txOrder: Rep[Int]          = column[Int]("tx_order")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def token: Rep[TokenId]        = column[TokenId]("token")

    def pk: PrimaryKey = primaryKey("token_tx_per_address_pk", (txHash, blockHash, address, token))

    def hashIdx: Index         = index("token_tx_per_address_hash_idx", txHash)
    def blockHashIdx: Index    = index("token_tx_per_address_block_hash_idx", blockHash)
    def addressIdx: Index      = index("token_tx_per_address_address_idx", address)
    def tokenIdx: Index        = index("token_tx_per_address_token_idx", token)
    def tokenAddressIdx: Index = index("token_tx_per_address_token_address_idx", (token, address))

    def * : ProvenShape[TokenTxPerAddressEntity] =
      (address, grouplessAddress, txHash, blockHash, timestamp, txOrder, mainChain, token)
        .<>((TokenTxPerAddressEntity.apply _).tupled, TokenTxPerAddressEntity.unapply)
  }

  def createIndexes(): DBIO[Unit] =
    DBIO.seq(
      CommonIndex.blockTimestampTxOrderIndex(this),
      CommonIndex.timestampIndex(this)
    )

  def createConcurrentIndexes()(implicit ec: ExecutionContext): DBActionW[Unit] =
    for {
      _ <- createTokenGrouplessAddressIndex()
    } yield ()

  def createTokenGrouplessAddressIndex(): DBActionW[Int] =
    sqlu"""
      CREATE INDEX token_tx_per_address_token_groupless_address_idx
      ON token_tx_per_addresses (token, groupless_address)
      WHERE groupless_address IS NOT NULL;
    """

  val table: TableQuery[TokenPerAddresses] = TableQuery[TokenPerAddresses]
}
