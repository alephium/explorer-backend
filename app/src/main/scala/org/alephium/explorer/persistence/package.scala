// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import slick.dbio.{DBIOAction, Effect, NoStream, Streaming}

package object persistence {

  type AllEffects = Effect.Schema with Effect.Read with Effect.Write with Effect.Transactional

  type DBAction[A, E <: Effect] = DBIOAction[A, NoStream, E]
  type DBActionR[A]             = DBIOAction[A, NoStream, Effect.Read]
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
