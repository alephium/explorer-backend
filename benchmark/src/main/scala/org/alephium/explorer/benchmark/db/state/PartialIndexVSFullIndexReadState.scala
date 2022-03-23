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

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings.{dbHost, dbName, dbPort}
import org.openjdk.jmh.annotations.{Scope, State}

import scala.concurrent.duration._
import scala.util.Random

sealed trait IndexType {
  val tableSuffix: String
}

object IndexType {
  case object None extends IndexType {
    override val tableSuffix: String = this.productPrefix
  }

  case object FullActive extends IndexType {
    override val tableSuffix: String = this.productPrefix
  }
  case object PartialActive extends IndexType {
    override val tableSuffix: String = this.productPrefix
  }

  case object FullUserType extends IndexType {
    override val tableSuffix: String = this.productPrefix
  }
  case object PartialUserType extends IndexType {
    override val tableSuffix: String = this.productPrefix
  }
}

case class User(id: Int, firstName: String, lastName: String, active: Boolean, userType: Int)

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class CountReadState(val db: DBExecutor, val indexType: IndexType)
    extends ReadBenchmarkState[Pagination](1000000 / 20, db) {

  import db.config.profile.api._

  def countActiveUsers(index: IndexType) =
    sql"select count(*) from users_#${index.tableSuffix} where active = true".as[Int]

  def listActiveUsers(index: IndexType, pagination: Pagination) =
    sql"""select * from users_#${index.tableSuffix}
          where active = true
          limit ${pagination.limit} offset ${pagination.limit * pagination.offset}""".as[Int]

  def countZeroUserTypeUsers(index: IndexType) =
    sql"select count(*) from users_#${index.tableSuffix} where user_type = 0".as[Int]

  def listZeroUserTypeUsers(index: IndexType, pagination: Pagination) =
    sql"""select * from users_#${index.tableSuffix}
          where user_type = 0
          limit ${pagination.limit} offset ${pagination.limit * pagination.offset}""".as[Int]

  override def generateData(currentCacheSize: Int): Pagination =
    Pagination.unsafe(
      offset = currentCacheSize,
      limit  = 20
    )

  override def persist(pages: Array[Pagination]): Unit = {
    println("Building schema")
    val createIndexSQL =
      indexType match {
        case IndexType.None =>
          ""

        case IndexType.FullActive =>
          s"""create index users_active_full_idx on users_${indexType.tableSuffix} (active);"""

        case IndexType.PartialActive =>
          s"""create index users_active_partial_idx on users_${indexType.tableSuffix} (active) where active = true;"""

        case IndexType.FullUserType =>
          s"""create index users_type_full_idx on users_${indexType.tableSuffix} (user_type);"""

        case IndexType.PartialUserType =>
          s"""create index users_type_partial_idx on users_${indexType.tableSuffix} (user_type) where user_type = 0;"""
      }

    val query =
      s"""|BEGIN;
          |drop index if exists users_active_full_idx;
          |drop index if exists users_active_partial_idx;
          |drop index if exists users_type_full_idx;
          |drop index if exists users_type_partial_idx;
          |DROP TABLE IF EXISTS users_${indexType.tableSuffix};
          |CREATE TABLE users_${indexType.tableSuffix}
          |(
          |    id         int not null,
          |    first_name varchar(255) not null,
          |    last_name  varchar(255) not null,
          |    active     boolean not null,
          |    user_type  int not null
          |);
          |$createIndexSQL
          |COMMIT;
          |""".stripMargin

    db.runNow(sqlu"#$query", 10.minutes)

    println("Inserting data")

    val insertSQL =
      (0 until 1000000)
        .map { id =>
          val firstName = Random.alphanumeric.take(10).mkString
          val lastName  = Random.alphanumeric.take(10).mkString
          val active    = id % 2 == 0
          val userType  = if (id % 2 == 0) 0 else 1
          s"""($id, '$firstName', '$lastName', $active, $userType)""".stripMargin
        }
        .mkString(s"INSERT INTO users_${indexType.tableSuffix} values ", ",", ";")

    db.runNow(sqlu"#$insertSQL", 10.minutes)

    println("Data inserted")
  }
}

class CountReadState_NoIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.None
    )

class CountReadState_FullActiveIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.FullActive
    )

class CountReadState_PartialActiveIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.PartialActive
    )

class CountReadState_FullUserTypeIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.FullUserType
    )

class CountReadState_PartialUserTypeIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.PartialUserType
    )
