// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.config

import scala.util.{Failure, Success}

import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.error.ExplorerError.InvalidBootMode

class BootModeSpec extends AlephiumSpec with ScalaCheckDrivenPropertyChecks {

  "validate" should {
    "fail" when {
      "input mode is invalid" in {
        forAll { (mode: String) =>
          BootMode.validate(mode) is Failure(InvalidBootMode(mode))
        }
      }
    }

    "succeed" when {
      "input mode is valid" in {
        BootMode.all foreach { mode =>
          BootMode.validate(mode.productPrefix) is Success(mode)
        }
      }
    }
  }

  "helpers" should {
    "resolve modes" in {
      BootMode("ReadOnly") is Some(BootMode.ReadOnly)
      BootMode("ReadWrite") is Some(BootMode.ReadWrite)
      BootMode("WriteOnly") is Some(BootMode.WriteOnly)
      BootMode.all.foreach { mode =>
        BootMode(mode.productPrefix) is Some(mode)
      }
      BootMode("Other") is None
    }

    "classify modes" in {
      BootMode.readable(BootMode.ReadOnly) is true
      BootMode.readable(BootMode.ReadWrite) is true
      BootMode.readable(BootMode.WriteOnly) is false

      BootMode.writable(BootMode.ReadOnly) is false
      BootMode.writable(BootMode.ReadWrite) is true
      BootMode.writable(BootMode.WriteOnly) is true
    }
  }
}
