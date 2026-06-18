// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.api.model.{GhostUncle, Height}
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
import org.alephium.util.TimeStamp

object BlockHeaderSchema extends SchemaMainChain[BlockHeader]("block_headers") {

  class BlockHeaders(tag: Tag) extends Table[BlockHeader](tag, name) {
    def hash: Rep[BlockHash] =
      column[BlockHash]("hash", O.PrimaryKey, O.SqlType("bytea"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def height: Rep[Height]        = column[Height]("height")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def nonce: Rep[ByteString]     = column[ByteString]("nonce")
    def version: Rep[Byte]         = column[Byte]("block_version")
    def depStateHash: Rep[Hash]    = column[Hash]("dep_state_hash")
    def txsHash: Rep[Hash]         = column[Hash]("txs_hash")
    def txsCount: Rep[Int]         = column[Int]("txs_count")
    def target: Rep[ByteString]    = column[ByteString]("target")
    def hashrate: Rep[BigInteger] =
      column[BigInteger](
        "hashrate",
        O.SqlType("DECIMAL(80,0)")
      ) // TODO How much decimal we need? this one is the same as for U256
    def parent: Rep[Option[BlockHash]] = column[Option[BlockHash]]("parent")
    def deps: Rep[ArraySeq[BlockHash]] = column[ArraySeq[BlockHash]]("deps")
    def ghostUncles: Rep[Option[ArraySeq[GhostUncle]]] =
      column[Option[ArraySeq[GhostUncle]]]("ghost_uncles")
    def conflictedTxs: Rep[Option[ArraySeq[TransactionId]]] =
      column[Option[ArraySeq[TransactionId]]]("conflicted_txs")

    def timestampIdx: Index = index("blocks_timestamp_idx", timestamp)
    def heightIdx: Index    = index("blocks_height_idx", height)

    def * : ProvenShape[BlockHeader] =
      (
        hash,
        timestamp,
        chainFrom,
        chainTo,
        height,
        mainChain,
        nonce,
        version,
        depStateHash,
        txsHash,
        txsCount,
        target,
        hashrate,
        parent,
        deps,
        ghostUncles,
        conflictedTxs
      )
        .<>((BlockHeader.apply _).tupled, BlockHeader.unapply)
  }

  /** Indexes all columns of this table so that `INDEX ONLY SCAN` is enough for queries to return
    * results.
    *
    * @see
    *   PR <a href="https://github.com/alephium/explorer-backend/pull/112">#112</a>.
    */
  private def fullIndex(): SqlAction[Int, NoStream, Effect] =
    sqlu"""
      create unique index if not exists #${name}_full_index
          on #${name} (main_chain asc, block_timestamp desc, hash asc, chain_from asc, chain_to asc, height asc);
      """

  /** Joins all indexes created via raw SQL
    */
  def createBlockHeadersIndexes(): DBIO[Unit] =
    DBIO.seq(fullIndex(), createMainChainIndex())

  val table: TableQuery[BlockHeaders] = TableQuery[BlockHeaders]
}
