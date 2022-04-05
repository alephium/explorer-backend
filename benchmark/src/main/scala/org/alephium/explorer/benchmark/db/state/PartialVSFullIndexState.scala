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

import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings.{dbHost, dbName, dbPort}
import org.openjdk.jmh.annotations.{Scope, State}

import scala.concurrent.duration._
import scala.util.Random

sealed trait IndexType
object IndexType {

  case object NoIndex extends IndexType

  case object FullMainChain                            extends IndexType
  case class FullMainChainReset(resetInterval: Int)    extends IndexType
  case object PartialMainChain                         extends IndexType
  case class PartialMainChainReset(resetInterval: Int) extends IndexType

  case object FullInterval                            extends IndexType
  case class FullIntervalReset(resetInterval: Int)    extends IndexType
  case object PartialInterval                         extends IndexType
  case class PartialIntervalReset(resetInterval: Int) extends IndexType

  case object FullComposite    extends IndexType
  case object PartialComposite extends IndexType
}

case class Transaction(id: Int,
                       string1: String,
                       string2: String,
                       main_chain: Boolean,
                       interval: Int)

/**
  * JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
class PartialVSFullIndexState(indexType: IndexType,
                              val db: DBExecutor = DBExecutor(name = dbName,
                                                              host = dbHost,
                                                              port = dbPort,
                                                              connectionPool =
                                                                DBConnectionPool.Disabled))
    extends ReadBenchmarkState[Transaction](1000000, db) {

  import db.config.profile.api._

  def selectMainChainTransaction(txnId: Int) =
    sql"select * from transactions where tx_id = $txnId and main_chain = true;".as[Int]

  val countMainChain =
    sql"select count(*) from transactions where main_chain = true".as[Int]

  val countInterval =
    sql"select count(*) from transactions where interval = 0".as[Int]

  override def generateData(currentCacheSize: Int): Transaction = {
    val mainChain =
      indexType match {
        case IndexType.FullMainChainReset(resetInterval) =>
          currentCacheSize % resetInterval == 0

        case IndexType.PartialMainChainReset(resetInterval) =>
          currentCacheSize % resetInterval == 0

        case _ =>
          currentCacheSize % 2 == 0
      }

    val interval =
      indexType match {
        case IndexType.FullIntervalReset(resetInterval) =>
          if (currentCacheSize % resetInterval == 0)
            0
          else
            currentCacheSize

        case IndexType.PartialIntervalReset(resetInterval) =>
          if (currentCacheSize % resetInterval == 0)
            0
          else
            currentCacheSize

        case _ =>
          if (currentCacheSize % 2 == 0)
            0
          else
            1
      }

    Transaction(
      id         = currentCacheSize,
      string1    = Random.alphanumeric.take(10).mkString,
      string2    = Random.alphanumeric.take(10).mkString,
      main_chain = mainChain,
      interval   = interval
    )
  }

  override def persist(transactions: Array[Transaction]): Unit = {
    println("Building schema")
    val createIndexSQL =
      indexType match {
        case IndexType.NoIndex =>
          ""

        case IndexType.FullMainChain | IndexType.FullMainChainReset(_) =>
          "create index full_main_chain_idx on transactions (main_chain);"

        case IndexType.PartialMainChain | IndexType.PartialMainChainReset(_) =>
          "create index partial_main_chain_idx on transactions (main_chain) where main_chain = true;"

        case IndexType.FullInterval | IndexType.FullIntervalReset(_) =>
          "create index full_interval_idx on transactions (interval);"

        case IndexType.PartialInterval | IndexType.PartialIntervalReset(_) =>
          "create index partial_interval_idx on transactions (interval) where interval = 0;"

        case IndexType.FullComposite =>
          "create index full_composite_idx on transactions (tx_id, main_chain);"

        case IndexType.PartialComposite =>
          "create index partial_composite_idx on transactions (tx_id) where main_chain = true;"
      }

    val query =
      s"""
         |BEGIN;
         |drop index if exists full_main_chain_idx;
         |drop index if exists partial_main_chain_idx;
         |drop index if exists full_interval_idx;
         |drop index if exists partial_interval_idx;
         |drop index if exists full_composite_idx;
         |drop index if exists partial_composite_idx;
         |
         |DROP TABLE IF EXISTS transactions;
         |CREATE TABLE transactions
         |(
         |    tx_id          int not null,
         |    hash           varchar(255) not null,
         |    block_hash     varchar(255) not null,
         |    main_chain     boolean not null,
         |    interval       int not null
         |);
         |$createIndexSQL
         |COMMIT;
         |""".stripMargin

    db.runNow(sqlu"#$query", 10.minutes)

    println("Inserting data")

    val batches =
      transactions
        .map { transaction =>
          s"""(${transaction.id}, '${transaction.string1}', '${transaction.string2}', ${transaction.main_chain}, ${transaction.interval})""".stripMargin
        }
        .grouped(100000)
        .toList

    batches.zipWithIndex foreach {
      case (transactions, chunk) =>
        println(s"${chunk + 1}/${batches.size}) Inserting transactions: ${transactions.length}")

        val insertQuery = transactions.mkString("INSERT INTO transactions values ", ",", ";")
        db.runNow(sqlu"#$insertQuery", 10.minutes)
    }

    println("Data inserted")
  }
}

/** MAIN_CHAIN OR BOOLEAN INDEX STATES */
class PartialVSFullIndexState_NoIndex_MainChain extends PartialVSFullIndexState(IndexType.NoIndex)

class PartialVSFullIndexState_FullMainChain extends PartialVSFullIndexState(IndexType.FullMainChain)
class PartialVSFullIndexState_FullMainChain_Reset_10
    extends PartialVSFullIndexState(IndexType.FullMainChainReset(10))
class PartialVSFullIndexState_FullMainChain_Reset_100
    extends PartialVSFullIndexState(IndexType.FullMainChainReset(100))
class PartialVSFullIndexState_FullMainChain_Reset_1000
    extends PartialVSFullIndexState(IndexType.FullMainChainReset(1000))

class PartialVSFullIndexState_PartialMainChain
    extends PartialVSFullIndexState(IndexType.PartialMainChain)
class PartialVSFullIndexState_PartialMainChain_Reset_10
    extends PartialVSFullIndexState(IndexType.PartialMainChainReset(10))
class PartialVSFullIndexState_PartialMainChain_Reset_100
    extends PartialVSFullIndexState(IndexType.PartialMainChainReset(100))
class PartialVSFullIndexState_PartialMainChain_Reset_1000
    extends PartialVSFullIndexState(IndexType.PartialMainChainReset(1000))

/** INTERVAL OR INTEGER INDEX STATES */
class PartialVSFullIndexState_NoIndex_Interval extends PartialVSFullIndexState(IndexType.NoIndex)
class PartialVSFullIndexState_FullInterval     extends PartialVSFullIndexState(IndexType.FullInterval)
class PartialVSFullIndexState_FullInterval_Reset_10
    extends PartialVSFullIndexState(IndexType.FullIntervalReset(10))
class PartialVSFullIndexState_FullInterval_Reset_100
    extends PartialVSFullIndexState(IndexType.FullIntervalReset(100))
class PartialVSFullIndexState_FullInterval_Reset_1000
    extends PartialVSFullIndexState(IndexType.FullIntervalReset(1000))

class PartialVSFullIndexState_PartialInterval
    extends PartialVSFullIndexState(IndexType.PartialInterval)
class PartialVSFullIndexState_PartialInterval_Reset_10
    extends PartialVSFullIndexState(IndexType.PartialIntervalReset(10))
class PartialVSFullIndexState_PartialInterval_Reset_100
    extends PartialVSFullIndexState(IndexType.PartialIntervalReset(100))
class PartialVSFullIndexState_PartialInterval_Reset_1000
    extends PartialVSFullIndexState(IndexType.PartialIntervalReset(1000))

/** COMPOSITE KEYS (tx_id & main_chain) INDEX STATES */
class PartialVSFullIndexState_NoIndex_Composite extends PartialVSFullIndexState(IndexType.NoIndex)
class PartialVSFullIndexState_FullComposite     extends PartialVSFullIndexState(IndexType.FullComposite)
class PartialVSFullIndexState_PartialComposite
    extends PartialVSFullIndexState(IndexType.PartialComposite)
