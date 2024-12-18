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

import org.scalatest.{BeforeAndAfterAll, Suite}
import sttp.client3._
import sttp.model.Method

import org.alephium.explorer.AlephiumFutures

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait HttpRouteFixture extends HttpFixture with BeforeAndAfterAll with AlephiumFutures {
  this: Suite =>

  def port: Int

  def Post(
      endpoint: String,
      maybeBody: Option[String]
  ): Response[Either[String, String]] = {
    httpPost(endpoint, maybeBody)(port).send(backend).futureValue
  }

  def Post(endpoint: String): Response[Either[String, String]] =
    Post(endpoint, None)

  def Post(endpoint: String, body: String): Response[Either[String, String]] =
    Post(endpoint, Some(body))

  def Put(endpoint: String, body: String) = {
    httpPut(endpoint, Some(body))(port).send(backend).futureValue
  }

  def Delete(endpoint: String, body: String) = {
    httpRequest(Method.DELETE, endpoint, Some(body))(port)
      .send(backend)
      .futureValue
  }

  def Get(
      endpoint: String,
      otherPort: Int = port,
      maybeBody: Option[String] = None
  ): Response[Either[String, String]] = {
    httpGet(endpoint, maybeBody = maybeBody)(otherPort)
      .send(backend)
      .futureValue
  }
}
