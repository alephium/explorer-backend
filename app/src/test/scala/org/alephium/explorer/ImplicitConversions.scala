// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions
import scala.reflect.ClassTag

import org.alephium.util.AVector

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
trait ImplicitConversions {

  implicit def avectorToArraySeq[A](avector: AVector[A]): ArraySeq[A] =
    ArraySeq.unsafeWrapArray(avector.toArray)

  implicit def iterableToArraySeq[A: ClassTag](iterable: Iterable[A]): ArraySeq[A] =
    ArraySeq.from(iterable)

  implicit def optionIterableToArraySeq[A: ClassTag](
      iterableOpt: Option[Iterable[A]]
  ): Option[ArraySeq[A]] =
    iterableOpt.map(ArraySeq.from(_))
}
