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

sealed trait TriggerType
object TriggerType {

  /** No trigger */
  case object None extends TriggerType

  /** No trigger but creates tables required for trigger, for benchmarking updates manually */
  case object NoneTablesOnly extends TriggerType

  /** Creates trigger to update counter table for each row inserted row */
  case object ForEachRow extends TriggerType

  /** Creates trigger to update counter table for each insert statement */
  case object ForEachStatement extends TriggerType

  /** Creates an empty trigger that does not do any IO. It seems cause for slowness during transaction is due to */
  case object ForEachRowNoIO extends TriggerType
}

/**
  * JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class TriggerWriteState(val db: DBExecutor, triggerType: TriggerType)
    extends WriteBenchmarkState[String](db) {

  import db.config.profile.api._

  override def generateData(): String =
    Random.alphanumeric.take(10).mkString

  def insert(value: String) =
    sqlu"insert into string_table values ($value)"

  def insertAndUpdateCount(value: String) = {
    val query =
      sqlu"insert into string_table values ($value)" andThen {
        sqlu"UPDATE string_table_row_counter set count = ((SELECT count(*) from string_table))"
      }

    query.transactionally
  }

  def insertMany(values: Iterable[String]) = {
    val valuesSQL = values.map(value => s"('$value')").mkString(", ")
    sqlu"insert into string_table (string) values #$valuesSQL"
  }

  def insertManyAndUpdateCount(values: Iterable[String]) = {
    val valuesSQL = values.map(value => s"('$value')").mkString(", ")
    val sql =
      sqlu"insert into string_table (string) values #$valuesSQL" andThen {
        sqlu"UPDATE string_table_row_counter set count = ((SELECT count(*) from string_table))"
      }

    sql.transactionally
  }

  def insertManyIndividual(values: Iterable[String]) = {
    val sql = values
      .map(value => s"insert into string_table (string) values ('$value');")
      .mkString("BEGIN;", "\n", "COMMIT;")
    sqlu"#$sql"
  }

  def insertManyIndividualPreparedStatement(values: Iterable[String]) = {
    val sql =
      values.foldLeft(null.asInstanceOf[DBIOAction[Int, NoStream, Effect]]) {
        case (action, value) =>
          if (action == null)
            sqlu"insert into string_table (string) values ('$value');"
          else
            action andThen sqlu"insert into string_table (string) values ('$value');"
      }

    sql.transactionally
  }

  override def beforeAll(): Unit = {
    println("Building schema")
    val triggerStatement =
      triggerType match {
        case TriggerType.None =>
          ""

        case TriggerType.NoneTablesOnly =>
          """
            |CREATE TABLE string_table_row_counter (
            |  count bigint
            |);
            |
            |INSERT INTO string_table_row_counter values ((SELECT count(*) from string_table));
            |""".stripMargin

        case TriggerType.ForEachRow =>
          """
            |CREATE TABLE string_table_row_counter (
            |  count bigint
            |);
            |
            |CREATE FUNCTION increment_string_table_row_counter() RETURNS TRIGGER AS
            |$$
            |DECLARE
            |BEGIN
            |    EXECUTE 'UPDATE string_table_row_counter set count = count + 1';
            |    RETURN NEW;
            |END;
            |$$
            |    LANGUAGE 'plpgsql';
            |
            |INSERT INTO string_table_row_counter values ((SELECT count(*) from string_table));
            |
            |CREATE TRIGGER string_table_row_counter_trigger BEFORE INSERT ON string_table
            |    FOR EACH ROW EXECUTE PROCEDURE increment_string_table_row_counter();
            |""".stripMargin

        case TriggerType.ForEachStatement =>
          """
            |CREATE TABLE string_table_row_counter (
            |  count bigint
            |);
            |
            |INSERT INTO string_table_row_counter values ((SELECT count(*) from string_table));
            |
            |CREATE FUNCTION increment_string_table_row_counter() RETURNS TRIGGER AS
            |$$
            |DECLARE
            |BEGIN
            |    EXECUTE 'UPDATE string_table_row_counter set count = ((SELECT count(*) from string_table))';
            |    RETURN NEW;
            |END;
            |$$
            |    LANGUAGE 'plpgsql';
            |
            |CREATE TRIGGER string_table_row_counter_trigger AFTER INSERT ON string_table
            |    FOR EACH STATEMENT EXECUTE PROCEDURE increment_string_table_row_counter();
            |""".stripMargin

        case TriggerType.ForEachRowNoIO =>
          """
            |CREATE TABLE string_table_row_counter (
            |  count bigint
            |);
            |
            |INSERT INTO string_table_row_counter values ((SELECT count(*) from string_table));
            |
            |CREATE FUNCTION increment_string_table_row_counter() RETURNS TRIGGER AS
            |$$
            |DECLARE
            |BEGIN
            |    RETURN NEW;
            |END;
            |$$
            |    LANGUAGE 'plpgsql';
            |
            |CREATE TRIGGER string_table_row_counter_trigger BEFORE INSERT ON string_table
            |    FOR EACH ROW EXECUTE PROCEDURE increment_string_table_row_counter();
            |""".stripMargin
      }

    val query =
      s"""
         |BEGIN;
         |drop function if exists increment_string_table_row_counter() cascade;
         |drop table if exists string_table_row_counter;
         |drop table if exists string_table;
         |
         |CREATE TABLE string_table (
         |  string text
         |);
         |$triggerStatement
         |COMMIT;
         |""".stripMargin

    db.runNow(sqlu"#$query", 10.minutes)
  }
}

class TriggerWriteState_Trigger_None
    extends TriggerWriteState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      triggerType = TriggerType.None
    )

class TriggerWriteState_Trigger_NoneTablesOnly
    extends TriggerWriteState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      triggerType = TriggerType.NoneTablesOnly
    )

class TriggerWriteState_Trigger_ForEachRow
    extends TriggerWriteState(
      db = DBExecutor(
        name           = dbName,
        host           = dbHost,
        port           = dbPort,
        connectionPool = DBConnectionPool.Disabled
      ),
      triggerType = TriggerType.ForEachRow
    )
