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

package org.alephium.explorer

import slick.dbio.{DBIOAction, Effect, NoStream, Streaming}
import slick.sql.SqlAction

package object persistence {

  type DBAction[A, E <: Effect] = DBIOAction[A, NoStream, E]
  type DBActionR[A]             = DBIOAction[A, NoStream, Effect.Read]
  type DBActionW[A]             = DBIOAction[A, NoStream, Effect.Write]
  type DBActionRW[A]            = DBIOAction[A, NoStream, Effect.Read with Effect.Write]
  type DBActionRWT[A] =
    DBIOAction[A, NoStream, Effect.Read with Effect.Write with Effect.Transactional]

  type DBActionS[A, E <: Effect] = DBIOAction[Vector[A], Streaming[A], E]
  type DBActionSR[A]             = DBIOAction[Vector[A], Streaming[A], Effect.Read]

  type SqlActionSR[A] = SqlAction[Vector[A], Streaming[A], Effect.Read]
}
