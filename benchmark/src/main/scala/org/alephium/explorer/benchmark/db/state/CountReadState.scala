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
  case object None extends IndexType

  case object Full    extends IndexType
  case object Partial extends IndexType

  case object Trigger extends IndexType
}

case class User(id: Int, firstName: String, lastName: String, active: Boolean)

/**
  * JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class CountReadState(val db: DBExecutor, indexType: IndexType) extends ReadBenchmarkState[User](1000000, db) {

  import db.config.profile.api._

  val countAllUsers =
    sql"select count(*) from users".as[Int]

  val countActiveUsers =
    sql"select count(*) from users where active = true".as[Int]

  val countAllUsers_In_UsersRowCounterTable =
    sql"select count from user_row_counter".as[Int]

  override def generateData(currentCacheSize: Int): User =
    User(
      id        = currentCacheSize,
      firstName = Random.alphanumeric.take(10).mkString,
      lastName  = Random.alphanumeric.take(10).mkString,
      active    = currentCacheSize % 2 == 0
    )

  override def persist(users: Array[User]): Unit = {
    println("Building schema")
    val activeUserIndex =
      indexType match {
        case IndexType.None =>
          ""

        case IndexType.Full =>
          """create index users_active_idx on users (active);
            |""".stripMargin

        case IndexType.Partial =>
          """
            |create index users_active_idx on users (active) where active = true;
            |""".stripMargin

        case IndexType.Trigger =>
          """
            |CREATE TABLE user_row_counter (
            |  count bigint
            |);
            |
            |CREATE OR REPLACE FUNCTION increment_user_row_counter() RETURNS TRIGGER AS
            |$$
            |DECLARE
            |BEGIN
            |    EXECUTE 'UPDATE user_row_counter set count = count + 1';
            |    RETURN NEW;
            |END;
            |$$
            |    LANGUAGE 'plpgsql';
            |
            |INSERT
            |INTO user_row_counter values ((SELECT count(*) from users));
            |
            |CREATE TRIGGER users_do_row_counter BEFORE INSERT ON users
            |    FOR EACH ROW EXECUTE PROCEDURE increment_user_row_counter();
            |""".stripMargin
      }

    val query =
      s"""
         |BEGIN;
         |drop index if exists users_active_idx;
         |drop table if exists user_row_counter;
         |drop function if exists increment_user_row_counter() cascade;
         |DROP TABLE IF EXISTS users;
         |CREATE TABLE users
         |(
         |    id         int,
         |    first_name varchar(255),
         |    last_name  varchar(255),
         |    active     boolean
         |);
         |$activeUserIndex
         |COMMIT;
         |""".stripMargin

    db.runNow(sqlu"#$query", 10.minutes)

    println("Inserting data")

    val batches =
      users
        .map { user =>
          s"""(${user.id}, '${user.firstName}', '${user.lastName}', ${user.active})""".stripMargin
        }
        .grouped(100000)
        .toList

    batches.zipWithIndex foreach {
      case (users, chunk) =>
        println(s"${chunk + 1}/${batches.size}) Inserting users: ${users.length}")

        val insertQuery = users.mkString("INSERT INTO users values ", ",", ";")
        db.runNow(sqlu"#$insertQuery", 10.minutes)
    }

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

class CountReadState_FullIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.Full
    )

class CountReadState_PartialIndex
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.Partial
    )

class CountReadState_TriggerRowCounter
    extends CountReadState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      indexType = IndexType.Trigger
    )
