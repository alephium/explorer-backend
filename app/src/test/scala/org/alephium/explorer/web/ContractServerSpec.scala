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

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq

import org.alephium.api.ApiError.BadRequest
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll

@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault", "org.wartremover.warts.Var"))
class ContractServerSpec()
    extends AlephiumActorSpecLike
    with DatabaseFixtureForAll
    with HttpServerFixture {

  val server = new ContractServer()

  val routes = server.routes

  "get contract info" in {
    forAll(addressContractProtocolGen) { address =>
      Get(s"/contracts/$address/info") check { response =>
        response.as[ArraySeq[ContractInfo]] is ArraySeq.empty
      }
    }
  }

  "get parent contract" in {
    forAll(addressContractProtocolGen) { address =>
      Get(s"/contracts/$address/parent") check { response =>
        response.as[ContractParent] is ContractParent(None)
      }
    }

    forAll(addressAssetProtocolGen()) { address =>
      Get(s"/contracts/$address/parent") check { response =>
        response.as[BadRequest] is BadRequest(
          s"Invalid value for: path parameter contract_address (Expect contract address, but was asset address: $address: $address)"
        )
      }
    }
  }

  "get sub-contracts" in {
    forAll(addressContractProtocolGen) { address =>
      Get(s"/contracts/$address/sub-contracts") check { response =>
        response.as[SubContracts] is SubContracts(ArraySeq.empty)
      }
    }

    forAll(addressAssetProtocolGen()) { address =>
      Get(s"/contracts/$address/sub-contracts") check { response =>
        response.as[BadRequest] is BadRequest(
          s"Invalid value for: path parameter contract_address (Expect contract address, but was asset address: $address: $address)"
        )
      }
    }
  }
}
