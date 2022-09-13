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

import scala.collection.BuildFrom
import scala.collection.mutable
import scala.reflect.ClassTag

import org.alephium.util.AVector

//TODO Add this to `org.alephium.util.AVector`
object RichAVector {
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  implicit def buildFromAVector[A: ClassTag]: BuildFrom[AVector[_], A, AVector[A]] =
    new BuildFrom[AVector[_], A, AVector[A]] {
      def newBuilder(from: AVector[_]): mutable.Builder[A, AVector[A]] =
        mutable.ArrayBuffer.newBuilder[A].mapResult(b => AVector.unsafe[A](b.toArray))
      def fromSpecific(from: AVector[_])(it: IterableOnce[A]): AVector[A] = AVector.from(it)
    }
}
