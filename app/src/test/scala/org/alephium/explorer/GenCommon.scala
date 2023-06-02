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

import java.net.InetAddress

import scala.concurrent.duration._

import org.scalacheck.{Arbitrary, Gen}
import sttp.model.Uri

/** Generators for types supplied by libraries outside Alephium eg: java or scala packages */
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object GenCommon {

  val genByte: Gen[Byte] =
    Arbitrary.arbitrary[Byte]

  val genBytePositive: Gen[Byte] =
    Gen.choose(0.toByte, Byte.MaxValue)

  val genPortNum: Gen[Int] =
    Gen.choose(1, 1000)

  val genInetAddress: Gen[InetAddress] =
    Gen.const(InetAddress.getByName("127.0.0.1"))

  val genUri: Gen[Uri] =
    for {
      address <- genInetAddress
      port    <- genPortNum
    } yield Uri(address.toString, port)

  val genTimeDurationUnit: Gen[String] =
    Gen.oneOf("seconds", "minutes", "hours", "days")

  val genTimeDurationForConfig: Gen[(Int, String)] =
    for {
      time <- Gen.choose(0, Int.MaxValue)
      unit <- genTimeDurationUnit
    } yield (time, unit)

  val genTimeDurationForConfigString: Gen[String] =
    genTimeDurationForConfig flatMap { case (value, unit) =>
      s"$value $unit"
    }

  val genFiniteDuration: Gen[FiniteDuration] =
    Arbitrary.arbitrary[Long].map(_.nanos)

  def genStringOfLength(length: Int, charGen: Gen[Char] = Gen.alphaChar): Gen[String] =
    Gen.listOfN(length, charGen).map(_.mkString)

  def genStringOfLengthBetween(
      min: Int,
      max: Int,
      charGen: Gen[Char] = Gen.alphaChar
  ): Gen[String] =
    Gen.choose(min, max) flatMap { length =>
      genStringOfLength(length, charGen)
    }

  /** Randomly pick one from the list.
    *
    * If the list is empty generate new from the `Gen[T]`.
    */
  def pickOneOrGen[T](pickFrom: Iterable[T])(orElseGen: Gen[T]): Gen[T] =
    if (pickFrom.isEmpty) {
      orElseGen
    } else {
      Gen.oneOf(pickFrom)
    }
}
