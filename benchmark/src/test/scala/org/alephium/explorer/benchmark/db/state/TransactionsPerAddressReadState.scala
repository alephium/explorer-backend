// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import org.openjdk.jmh.annotations.{Scope, State}
import org.scalacheck.Gen

import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.model.TransactionPerAddressEntity
import org.alephium.explorer.persistence.schema.TransactionPerAddressSchema
import org.alephium.protocol.model.Address
import org.alephium.util.TimeStamp

/** @param addressCount
  *   Number of Address to generated
  * @param transactionsPerAddress
  *   Number of Transactions to create per Address with unique `block_timestamp`. This implies 1
  *   transactions per block.
  * @param transactionsPerAddressPerDay
  *   Number of Transactions to create per Address with same `block_timestamp` This implies multiple
  *   transactions per block.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class TransactionsPerAddressReadState(
    val addressCount: Int,
    val transactionsPerAddress: Int,
    val transactionsPerAddressPerDay: Int,
    val db: DBExecutor
) extends ReadBenchmarkState[String](addressCount, db) {

  import config.profile.api._

  def this() =
    this(
      addressCount = 1,
      transactionsPerAddress = 10000,
      transactionsPerAddressPerDay = 5,
      db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled)
    )

  override def generateData(currentCacheSize: Int): String =
    addressGen.sample.get.toBase58

  private def genTransactions(address: String): Seq[TransactionPerAddressEntity] =
    (0 to transactionsPerAddress) flatMap { timeStamp =>
      val transactionsPerDayGen =
        genTransactionPerAddressEntity(
          addressGen = Gen.const(Address.fromBase58(address).toOption.get),
          timestampGen = Gen.const(TimeStamp.unsafe(timeStamp.toLong))
        )

      Gen.listOfN(transactionsPerAddressPerDay, transactionsPerDayGen).sample.get
    }

  override def persist(addresses: Array[String]): Unit = {
    // start a fresh database (TODO: moved these to TestQueries)
    val _ = db.dropTableIfExists(TransactionPerAddressSchema.table)
    val _ = db.runNow(TransactionPerAddressSchema.table.schema.create, batchWriteTimeout)

    // generate data for all addresses
    addresses.zipWithIndex foreach { case (address, addressIndex) =>
      val transactionsForThisAddress = genTransactions(address)
      logger.info(s"Persisting addresses: ${addressIndex + 1}/${addresses.length}")
      val action = TransactionPerAddressSchema.table ++= transactionsForThisAddress
      db.runNow(action, batchWriteTimeout)
    }

    logger.info("Persisting data complete")
  }

}
