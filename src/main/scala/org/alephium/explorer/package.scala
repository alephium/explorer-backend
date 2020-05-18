package org.alephium

package object explorer {
  @inline @specialized def sideEffect[E](effect: E): Unit = {
    val _ = effect
    () //Return unit to prevent warning due to discarding value
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](self: A) {
    def ===(other: A): Boolean = self == other
    def =/=(other: A): Boolean = self != other
  }
}
