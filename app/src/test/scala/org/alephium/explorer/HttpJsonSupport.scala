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

import scala.collection.immutable.Seq

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypeRange, MediaType}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.ByteString

import org.alephium.json.Json
import org.alephium.json.Json._

//Inspired by
//https://github.com/hseeberger/akka-http-json/blob/v1.39.2/akka-http-upickle/src/main/scala/de/heikoseeberger/akkahttpupickle/UpickleCustomizationSupport.scala
trait HttpJsonSupport {
  type Api = Json.type

  def api: Api = Json

  private val mediaTypes: Seq[MediaType.WithFixedCharset] =
    List(`application/json`)

  private val stringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(mediaTypes.map(ContentTypeRange.apply): _*)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset.name)
      }

  private val stringMarshaller =
    Marshaller.oneOf(mediaTypes: _*)(Marshaller.stringMarshaller)

  implicit def unmarshaller[A: Reader]: FromEntityUnmarshaller[A] =
    stringUnmarshaller.map(read[A](_))

  implicit def marshaller[A: Writer]: ToEntityMarshaller[A] =
    stringMarshaller.compose(write(_))
}
