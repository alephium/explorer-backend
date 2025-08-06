// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.queries.QueryUtil._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.TimeStampTableFixture._
import org.alephium.util._

class QueryUtilSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  "date group" in {
    exec(sqlu"DROP TABLE IF EXISTS timestamps")
    exec(timestampTable.schema.create)

    val t1 = "2020-12-31T23:59:59.999Z"
    val t2 = "2021-01-01T01:01:01.000Z"
    val t3 = "2021-01-01T02:00:00.000Z"
    val t4 = "2021-01-01T03:01:00.000Z"
    val t5 = "2021-01-01T03:14:00.000Z"
    val t6 = "2021-01-02T03:14:00.000Z"
    val t7 = "2021-02-24T03:14:00.000Z"
    val t8 = "2021-02-26T03:14:00.000Z"

    val timestamps = Seq(t1, t2, t3, t4, t5, t6, t7, t8).map(ts)

    exec(timestampTable ++= timestamps)

    val hourly = exec(sql"""SELECT #${extractEpoch(
        hourlyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp])
    val daily = exec(sql"""SELECT #${extractEpoch(
        dailyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp])
    val weekly = exec(sql"""SELECT #${extractEpoch(
        weeklyQuery
      )} as ts FROM timestamps GROUP BY ts ORDER BY ts""".as[TimeStamp])

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
