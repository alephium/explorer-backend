package org.alephium.explorer.persistence

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

trait DBRunner {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, E <: Effect](action: DBAction[R, E])(
      implicit executionContext: ExecutionContext): Future[R] =
    config.db.run(action).recover {
      case error => throw new RuntimeException(error)
    }
}
