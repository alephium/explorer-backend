// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

package object explorer {
  implicit final class AnyOps[A](val self: A) extends AnyVal {
    def ===(other: A): Boolean = self == other
    def =/=(other: A): Boolean = self != other
  }

  def foldFutures[A, B: ClassTag](
      seqA: ArraySeq[A]
  )(f: A => Future[B])(implicit executionContext: ExecutionContext): Future[ArraySeq[B]] =
    seqA.foldLeft(Future.successful(ArraySeq.empty[B])) { case (acc, a) =>
      acc.flatMap(p => f(a).map(b => p :+ b))
    }
}
