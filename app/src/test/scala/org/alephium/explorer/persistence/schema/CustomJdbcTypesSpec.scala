// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GenDBModel}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.OutputQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.TimeStampTableFixture._
import org.alephium.protocol.ALPH
import org.alephium.util._

class CustomJdbcTypesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "convert TimeStamp" in {

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
      sql"SELECT * from timestamps WHERE block_timestamp = $t1"
        .as[TimeStamp]
    ).futureValue is Vector(t1)

    run(timestampTable.filter(_.timestamp === t1).result).futureValue is Seq(t1)

    run(
      sql"SELECT * from timestamps WHERE block_timestamp = $t2"
        .as[TimeStamp]
    ).futureValue is Vector(t2)

    run(timestampTable.filter(_.timestamp === t2).result).futureValue is Seq(t2)

    run(
      sql"SELECT * from timestamps WHERE block_timestamp <= $t1"
        .as[TimeStamp]
    ).futureValue is Vector(t1, t2)

    run(timestampTable.filter(_.timestamp <= t1).result).futureValue is Seq(t1, t2)
  }

  "set/get tokens" in {

    forAll(GenDBModel.outputEntityGen) { output =>
      run(OutputSchema.table.delete).futureValue

      run(OutputQueries.insertOutputs(Seq(output))).futureValue
      run(OutputSchema.table.result).futureValue.head is output

      if (output.tokens.isEmpty) {
        run(
          sql"SELECT CASE WHEN tokens IS NULL THEN 'true' ELSE 'false' END FROM outputs"
            .as[Boolean]
        ).futureValue.head is true
      }

    }
  }
}
