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

package org.alephium.explorer.persistence.schema

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.protocol.ALPH
import org.alephium.util._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.lifted.ProvenShape

import scala.concurrent.ExecutionContext

class CustomTypesSpec extends AlephiumSpec with ScalaFutures with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "convert TimeStamp" in new Fixture {
    import config.profile.api._

    run(sqlu"DROP TABLE IF EXISTS timestamps;").futureValue
    run(timestampTable.schema.create).futureValue

    val t1 = ALPH.LaunchTimestamp
    val t2 = ts("2020-12-31T11:00:00.000Z")

    val timestamps = Seq(t1, t2)
    run(timestampTable ++= timestamps).futureValue

    val instant1 = java.time.Instant.ofEpochMilli(t1.millis)
    val instant2 = java.time.Instant.ofEpochMilli(t2.millis)

    /*
     * Using slick returns the correct timestamp, while the raw sql doesnt.
     * This is because the data is stored in db as a local time, so shifted
     * by 1 here in Switzerland
     */
    run(
      sql"SELECT * from timestamps WHERE timestamp =$t1;"
        .as[TimeStamp]).futureValue is Vector(t1)

    run(timestampTable.filter(_.timestamp === t1).result).futureValue is Seq(t1)

    run(
      sql"SELECT * from timestamps WHERE timestamp =$t2;"
        .as[TimeStamp]).futureValue is Vector(t2)

    run(timestampTable.filter(_.timestamp === t2).result).futureValue is Seq(t2)

    run(
      sql"SELECT * from timestamps WHERE timestamp <=$t1;"
        .as[TimeStamp]).futureValue is Vector(t1, t2)

    val str = run(
      sql"SELECT * from timestamps"
        .as[String]).futureValue

      println(s"${Console.RED}${Console.BOLD}*** str ***\n\t${Console.RESET}${str}")

      str.last is "2020-12-31 12:00:00" //OOUUUCCHHHH should be 11:00

    run(timestampTable.filter(_.timestamp <= t1).result).futureValue is Seq(t1, t2)
  }

  it should "persist and read timestamp" in new Fixture {

    import config.profile.api._

    /**
      * Reading written timestamps fails when connectionPool is enabled (HikariCP),
      * see application.conf.
      *
      * This problem seems to be happening only with timestamps. This test narrows
      * downs the problem for debugging.
      */
    val insertTimestamp = TimeStamp.unsafe(155378831591591L)
    //More test timestamps that fail this test
    //247051996136270L
    //64021373170510L
    //198075290701573L
    //249229276538059L

    (1 to 100) foreach { i =>
      println("Iteration: " + i)

      //create a fresh table
      run(sqlu"DROP TABLE IF EXISTS timestamps;").futureValue
      run(timestampTable.schema.create).futureValue

      //write timestamp
      run(timestampTable += insertTimestamp).futureValue
      //read the table
      val readTimestamps = run(timestampTable.result).futureValue

      try {
        //read timestamp should contain only the insert
        readTimestamps should contain only insertTimestamp
      } catch {
        case throwable: Throwable =>
          readTimestamps should have size 1
          val actualTimestamp = readTimestamps.head

          //print out errored timestamp
          println("Inserted: " + insertTimestamp)
          println("Actual:   " + actualTimestamp)
          println(s"Equality: ${actualTimestamp.value == insertTimestamp.value}")

          throw throwable
      }
    }
  }

  trait Fixture extends CustomTypes with DatabaseFixture with OutputSchema with DBRunner {
    override val config = databaseConfig

    import config.profile.api._

    def ts(str: String): TimeStamp = TimeStamp.unsafe(java.time.Instant.parse(str).toEpochMilli)

    class TimeStamps(tag: Tag) extends Table[TimeStamp](tag, "timestamps") {
      def timestamp: Rep[TimeStamp]  = column[TimeStamp]("timestamp")
      def * : ProvenShape[TimeStamp] = timestamp
    }

    val timestampTable: TableQuery[TimeStamps] = TableQuery[TimeStamps]
  }
}
