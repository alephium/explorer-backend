// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq

import org.alephium.api.ApiError.{BadRequest, NotFound}
import org.alephium.api.model.ValAddress
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil.timestampGen
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForAll, DBRunner}
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.explorer.persistence.queries.ContractQueries

@SuppressWarnings(
  Array(
    "org.wartremover.warts.PlatformDefault",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class ContractServerSpec()
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with DBRunner
    with HttpServerFixture {

  val server = new ContractServer()

  val routes = server.routes

  "get contract liveness" should {
    "return the liveness when only 1 entry is in DB" in {
      val (groupIndex, events) = createEventsGen().sample.get
      run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue

      events.foreach { event =>
        val address = event.fields.head.asInstanceOf[ValAddress].value
        Get(s"/contracts/${address}/current-liveness") check { response =>
          val contractInfo = response.as[ContractLiveness]
          contractInfo.creation.blockHash is event.blockHash
          contractInfo.creation.txHash is event.txHash
        }
      }
    }

    "return the latest liveness when multiple exists for one contract" in {
      val (groupIndex, baseEvents) = createEventsGen().sample.get
      val events = baseEvents ++ baseEvents.map(
        _.copy(blockHash = blockHashGen.sample.get, timestamp = timestampGen.sample.get)
      )
      val blockHeaders = events.map(event =>
        blockHeaderGen.sample.get.copy(hash = event.blockHash, mainChain = true)
      )

      run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue
      run(BlockQueries.insertBlockHeaders(blockHeaders)).futureValue

      events.foreach { event =>
        val address = event.fields.head.asInstanceOf[ValAddress].value
        Get(s"/contracts/${address}/current-liveness") check { response =>
          val infos = run(ContractQueries.getContractEntity(address)).futureValue

          infos.size is 2
          infos.maxBy(_.creationTimestamp).toApi is response.as[ContractLiveness]
        }
      }
    }

    "return not found if liveness not in main chain" in {
      val (groupIndex, baseEvents) = createEventsGen().sample.get
      val events = baseEvents ++ baseEvents.map(
        _.copy(blockHash = blockHashGen.sample.get)
      )
      val blockHeaders = events.map(event =>
        blockHeaderGen.sample.get.copy(hash = event.blockHash, mainChain = false)
      )

      run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue
      run(BlockQueries.insertBlockHeaders(blockHeaders)).futureValue

      events.foreach { event =>
        val address = event.fields.head.asInstanceOf[ValAddress].value
        Get(s"/contracts/${address}/current-liveness") check { response =>
          response.as[NotFound] is NotFound(s"Contract not found in main chain: $address")
        }
      }
    }

    "return not found when contract doesn't exist" in {
      forAll(addressContractProtocolGen) { address =>
        Get(s"/contracts/$address/current-liveness") check { response =>
          response.as[NotFound] is NotFound(s"Contract not found: $address")
        }
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
          s"Invalid value for: path parameter contract_address (Expected a contract address, but got an asset address: $address: $address)"
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
          s"Invalid value for: path parameter contract_address (Expected a contract address, but got an asset address: $address: $address)"
        )
      }
    }
  }
}
