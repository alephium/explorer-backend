// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.concurrent.Future

import org.reactivestreams.Publisher
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

trait DBRunner {
  def databaseConfig: DatabaseConfig[PostgresProfile]

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, E <: Effect](action: DBAction[R, E]): Future[R] =
    databaseConfig.db.run(action)
}

object DBRunner {
  @inline def apply(dc: DatabaseConfig[PostgresProfile]): DBRunner =
    new DBRunner {
      override def databaseConfig = dc
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, E <: Effect](databaseConfig: DatabaseConfig[PostgresProfile])(
      action: DBAction[R, E]
  ): Future[R] =
    databaseConfig.db.run(action)

  /** Temporary function until all things are made stateless */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def run[R, E <: Effect](action: DBAction[R, E])(implicit
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[R] =
    databaseConfig.db.run(action)

  def stream[R](action: StreamAction[R])(implicit
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Publisher[R] =
    databaseConfig.db.stream(action)
}
