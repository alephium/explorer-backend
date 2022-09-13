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

package org.alephium.explorer.benchmark.db.state

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.explorer.api.model.Address
import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state.ListBlocksReadStateSettings._
import org.alephium.explorer.persistence.schema.TransactionPerAddressSchema
import org.alephium.util.AVector

/**
  * JMH state for benchmarking via [[org.alephium.explorer.persistence.queries.TransactionQueries.areAddressesActiveAction]]
  */
class AreAddressesActiveReadState(val db: DBExecutor)
    extends ReadBenchmarkState[AVector[Address]](testDataCount = maxPages, db = db) {

  import config.profile.api._

  // scalastyle:off magic.number
  override def generateData(currentCacheSize: Int): AVector[Address] =
    AVector.fill(40)(DataGenerator.genAddress()) //Generate 40 address per query

  override def persist(data: Array[AVector[Address]]): Unit = {
    val transactions = //for every address generate a TransactionPerAddressEntity
      data flatMap { addresses =>
        addresses map DataGenerator.genTransactionPerAddressEntity
      }

    val _ = db.dropTableIfExists(TransactionPerAddressSchema.table)

    //persist TransactionPerAddressEntities
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
