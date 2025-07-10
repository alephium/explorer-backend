// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import scala.collection.immutable.ArraySeq

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state.ListBlocksReadStateSettings._
import org.alephium.explorer.persistence.schema.TransactionPerAddressSchema

/** JMH state for benchmarking via
  * [[org.alephium.explorer.persistence.queries.TransactionQueries.areAddressesActiveAction]]
  */
class AreAddressesActiveReadState(val db: DBExecutor)
    extends ReadBenchmarkState[ArraySeq[ApiAddress]](testDataCount = maxPages, db = db) {

  import config.profile.api._

  // scalastyle:off magic.number
  override def generateData(currentCacheSize: Int): ArraySeq[ApiAddress] =
    ArraySeq.fill(40)(DataGenerator.genAddress()) // Generate 40 address per query

  override def persist(data: Array[ArraySeq[ApiAddress]]): Unit = {
    val transactions = // for every address generate a TransactionPerAddressEntity
      data flatMap { addresses =>
        addresses map DataGenerator.genTransactionPerAddressEntity
      }

    val _ = db.dropTableIfExists(TransactionPerAddressSchema.table)

    // persist TransactionPerAddressEntities
    val createDBBenchmarkState =
      TransactionPerAddressSchema.table.schema.create
        .andThen(TransactionPerAddressSchema.createMainChainIndex())
        .andThen(TransactionPerAddressSchema.table ++= transactions)

    val _ = db.runNow(createDBBenchmarkState, batchWriteTimeout)
  }
}

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class AreAddressesActiveReadState_DisabledCP(override val db: DBExecutor)
    extends AreAddressesActiveReadState(db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class AreAddressesActiveReadState_HikariCP(override val db: DBExecutor)
    extends AreAddressesActiveReadState(db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
