// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GenDBModel, GroupSetting}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.TokenSupplyByTokenEntity
import org.alephium.util.U256

class TokenSupplyByTokenSchemaSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  implicit val groupSetting: GroupSetting = GroupSetting(1)

  "store token supply snapshots" in {
    val tokenOutput = GenDBModel.tokenOutputEntityGen().sample.get
    val snapshot =
      TokenSupplyByTokenEntity(tokenOutput.token, tokenOutput.timestamp, U256.unsafe(42))

    exec(TokenSupplyByTokenSchema.table.schema.create)
    exec(TokenSupplyByTokenSchema.table ++= Seq(snapshot))

    exec(TokenSupplyByTokenSchema.table.result) is Seq(snapshot)
  }
}
