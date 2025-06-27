// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import org.alephium.util.AVector

//TODO Add this to `org.alephium.util.AVector`
object RichAVector {
  implicit class Impl[A](val avector: AVector[A]) extends AnyVal {
    def toArraySeq: ArraySeq[A] = {
      ArraySeq.unsafeWrapArray(avector.toArray)
    }
  }
}
