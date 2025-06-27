// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import scala.collection.immutable.ArraySeq

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.Token
import org.alephium.explorer.persistence.model.{GrouplessAddress, UOutputEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object UOutputSchema extends Schema[UOutputEntity]("uoutputs") {

  class UOutputs(tag: Tag) extends Table[UOutputEntity](tag, name) {
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def hint: Rep[Int]             = column[Int]("hint")
    def key: Rep[Hash]             = column[Hash]("key", O.SqlType("BYTEA"))
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def address: Rep[Address] = column[Address]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def tokens: Rep[Option[ArraySeq[Token]]] = column[Option[ArraySeq[Token]]]("tokens")
    def lockTime: Rep[Option[TimeStamp]]     = column[Option[TimeStamp]]("lock_time")
    def message: Rep[Option[ByteString]]     = column[Option[ByteString]]("message")
    def uoutputOrder: Rep[Int]               = column[Int]("uoutput_order")

    def pk: PrimaryKey = primaryKey("uoutputs_pk", (txHash, address, uoutputOrder))

    def txHashIdx: Index = index("uoutputs_tx_hash_idx", txHash)

    def * : ProvenShape[UOutputEntity] =
      (
        txHash,
        hint,
        key,
        amount,
        address,
        grouplessAddress,
        tokens,
        lockTime,
        message,
        uoutputOrder
      )
        .<>((UOutputEntity.apply _).tupled, UOutputEntity.unapply)
  }

  val table: TableQuery[UOutputs] = TableQuery[UOutputs]
}
