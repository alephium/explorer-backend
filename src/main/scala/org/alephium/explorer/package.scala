package org.alephium

import org.alephium.crypto.Keccak256

package object explorer {
  @inline @specialized def sideEffect[E](effect: E): Unit = {
    val _ = effect
    () //Return unit to prevent warning due to discarding value
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](val self: A) extends AnyVal {
    def ===(other: A): Boolean = self == other
    def =/=(other: A): Boolean = self != other
  }

  type Hash = Keccak256
  val Hash: Keccak256.type = Keccak256
}
