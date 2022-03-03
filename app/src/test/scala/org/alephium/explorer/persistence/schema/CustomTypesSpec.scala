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

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.lifted.ProvenShape

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.protocol.ALPH
import org.alephium.util._

class CustomTypesSpec extends AlephiumSpec with ScalaFutures with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  it should "convert TimeStamp" in new Fixture {
    import config.profile.api._

    run(sqlu"DROP TABLE IF EXISTS timestamps;").futureValue
    run(timestampTable.schema.create).futureValue

    val t1 = ALPH.LaunchTimestamp
    val t2 = ts("2020-12-31T23:59:59.999Z")

    val timestamps = Seq(t1, t2)
    run(timestampTable ++= timestamps).futureValue

    /*
     * Using slick returns the correct timestamp, while the raw sql doesnt.
     * This is because the data is stored in db as a local time, so shifted
     * by 1 here in Switzerland
     */
    run(
      sql"SELECT * from timestamps WHERE timestamp = $t1"
        .as[TimeStamp]).futureValue is Vector(t1)

    run(timestampTable.filter(_.timestamp === t1).result).futureValue is Seq(t1)

    run(
      sql"SELECT * from timestamps WHERE timestamp = $t2"
        .as[TimeStamp]).futureValue is Vector(t2)

    run(timestampTable.filter(_.timestamp === t2).result).futureValue is Seq(t2)

    run(
      sql"SELECT * from timestamps WHERE timestamp <= $t1"
        .as[TimeStamp]).futureValue is Vector(t1, t2)

    run(timestampTable.filter(_.timestamp <= t1).result).futureValue is Seq(t1, t2)
  }

  trait Fixture extends CustomTypes with DatabaseFixture with DBRunner {
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
