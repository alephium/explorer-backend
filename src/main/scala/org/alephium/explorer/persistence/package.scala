package org.alephium.explorer

import slick.dbio.{DBIOAction, Effect, NoStream}

package object persistence {

  type DBAction[A, E <: Effect] = DBIOAction[A, NoStream, E]
  type DBActionR[A]             = DBIOAction[A, NoStream, Effect.Read]
  type DBActionW[A]             = DBIOAction[A, NoStream, Effect.Write]
  type DBActionRW[A]            = DBIOAction[A, NoStream, Effect.Read with Effect.Write]
}
