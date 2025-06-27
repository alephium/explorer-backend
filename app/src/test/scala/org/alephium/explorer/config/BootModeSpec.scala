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
}
