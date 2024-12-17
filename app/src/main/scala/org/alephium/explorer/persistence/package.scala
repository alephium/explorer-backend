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

import scala.collection.immutable.ArraySeq

import slick.dbio.{DBIOAction, Effect, NoStream, Streaming}

package object persistence {

  type AllEffects = Effect.Schema with Effect.Read with Effect.Write with Effect.Transactional

  type DBAction[A, E <: Effect] = DBIOAction[A, NoStream, E]
  type DBActionR[A]             = DBIOAction[A, NoStream, Effect.Read]
  type DBActionRT[A]            = DBIOAction[A, NoStream, Effect.Read with Effect.Transactional]
  type DBActionW[A]             = DBIOAction[A, NoStream, Effect.Write]
  type DBActionT[A] =
    DBIOAction[A, NoStream, Effect.Write with Effect.Transactional] // Transaction DBAction
  type DBActionRW[A] = DBIOAction[A, NoStream, Effect.Read with Effect.Write]
  type DBActionWT[A] =
    DBIOAction[A, NoStream, Effect.Write with Effect.Transactional]
  type DBActionRWT[A] =
    DBIOAction[A, NoStream, Effect.Read with Effect.Write with Effect.Transactional]
  type DBActionAll[A] =
    DBIOAction[A, NoStream, AllEffects]

  type DBActionSR[A]   = DBIOAction[ArraySeq[A], NoStream, Effect.Read]
  type StreamAction[A] = DBIOAction[ArraySeq[A], Streaming[A], Effect.Read]
}
