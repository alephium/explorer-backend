// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.persistence.model.AddressTotalTransactionEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util.TimeStamp

object AddressTotalTransactionSchema
    extends SchemaMainChain[AddressTotalTransactionEntity]("address_total_transaction") {

  class CountTransactions(tag: Tag) extends Table[AddressTotalTransactionEntity](tag, name) {
    def address: Rep[ApiAddress]   = column[ApiAddress]("address", O.PrimaryKey)
    def total: Rep[Int]            = column[Int]("total")
    def lastUpdate: Rep[TimeStamp] = column[TimeStamp]("last_update")

    def * : ProvenShape[AddressTotalTransactionEntity] =
      (address, total, lastUpdate).<>(
        (AddressTotalTransactionEntity.apply _).tupled,
        AddressTotalTransactionEntity.unapply
      )
  }

  val table: TableQuery[CountTransactions] = TableQuery[CountTransactions]
}
