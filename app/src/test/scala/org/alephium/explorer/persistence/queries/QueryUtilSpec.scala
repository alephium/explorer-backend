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

package org.alephium.explorer.persistence.queries

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.QueryUtil._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.TimeStampTableFixture._
import org.alephium.util._

class QueryUtilSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "date group" in {
    run(sqlu"DROP TABLE IF EXISTS timestamps").futureValue
    run(timestampTable.schema.create).futureValue

    val t1 = "2020-12-31T23:59:59.999Z"
    val t2 = "2021-01-01T01:01:01.000Z"
    val t3 = "2021-01-01T02:00:00.000Z"
    val t4 = "2021-01-01T03:01:00.000Z"
    val t5 = "2021-01-01T03:14:00.000Z"
    val t6 = "2021-01-02T03:14:00.000Z"
    val t7 = "2021-02-24T03:14:00.000Z"
    val t8 = "2021-02-26T03:14:00.000Z"

    val timestamps = Seq(t1, t2, t3, t4, t5, t6, t7, t8).map(ts)

    run(timestampTable ++= timestamps).futureValue

    val hourly = run(sql"""SELECT #${extractEpoch(
        hourlyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp]).futureValue
    val daily = run(sql"""SELECT #${extractEpoch(
        dailyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp]).futureValue
    val weekly = run(sql"""SELECT #${extractEpoch(
        weeklyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp]).futureValue

    hourly is Vector(
      "2021-01-01T00:00:00.000Z",
      "2021-01-01T02:00:00.000Z",
      "2021-01-01T04:00:00.000Z",
      "2021-01-02T04:00:00.000Z",
      "2021-02-24T04:00:00.000Z",
      "2021-02-26T04:00:00.000Z"
    ).map(ts)
    daily is Vector(
      "2021-01-01T00:00:00.000Z",
      "2021-01-02T00:00:00.000Z",
      "2021-01-03T00:00:00.000Z",
      "2021-02-25T00:00:00.000Z",
      "2021-02-27T00:00:00.000Z"
    ).map(ts)
    weekly is Vector("2021-01-04T00:00:00.000Z", "2021-03-01T00:00:00.000Z").map(ts)
  }
}
