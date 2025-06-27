// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import sttp.tapir.{Schema, Validator}
import upickle.core.Abort

import org.alephium.json.Json._

final case class LogbackValue(
    name: String,
    level: LogbackValue.Level
)

object LogbackValue {
  implicit val readWriter: ReadWriter[LogbackValue] = macroRW

  sealed trait Level

  object Level {
    case object Trace extends Level {
      override def toString(): String = "TRACE"
    }
    case object Debug extends Level {
      override def toString(): String = "DEBUG"
    }
    case object Info extends Level {
      override def toString(): String = "INFO"
    }
    case object Warn extends Level {
      override def toString(): String = "WARN"
    }
    case object Error extends Level {
      override def toString(): String = "ERROR"
    }

    @SuppressWarnings(
      Array(
        "org.wartremover.warts.JavaSerializable",
        "org.wartremover.warts.Product",
        "org.wartremover.warts.Serializable"
      )
    ) // Wartremover is complaining, don't now why :/
    val levels: ArraySeq[Level] = ArraySeq(Trace, Debug, Info, Warn, Error)

    implicit val levelReadWriter: ReadWriter[Level] = readwriter[String].bimap(
      _.toString,
      {
        case "TRACE" => Trace
        case "DEBUG" => Debug
        case "INFO"  => Info
        case "WARN"  => Warn
        case "ERROR" => Error
        case _       => throw new Abort(s"Cannot decode level, expected one of: ${levels}")
      }
    )

    implicit def levelSchema: Schema[Level] =
      Schema.string.validate(Validator.enumeration(levels.toList))
  }
}
