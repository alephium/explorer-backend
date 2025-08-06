// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model.{ContractLiveness, ContractParent, Pagination, SubContracts}
import org.alephium.protocol.model.Address

trait ContractsEndpoints extends BaseEndpoint with QueryParams {

  private val contractsEndpoint =
    baseEndpoint
      .tag("Contracts")
      .in("contracts")

  val getContractInfo: BaseEndpoint[Address.Contract, ContractLiveness] =
    contractsEndpoint.get
      .in(path[Address.Contract]("contract_address"))
      .in("current-liveness")
      .out(jsonBody[ContractLiveness])
      .summary("Get contract liveness")

  val getParentAddress: BaseEndpoint[Address.Contract, ContractParent] =
    contractsEndpoint.get
      .in(path[Address.Contract]("contract_address"))
      .in("parent")
      .out(jsonBody[ContractParent])
      .summary("Get contract parent address if exist")

  val getSubContracts: BaseEndpoint[(Address.Contract, Pagination), SubContracts] =
    contractsEndpoint.get
      .in(path[Address.Contract]("contract_address"))
      .in("sub-contracts")
      .in(pagination)
      .out(jsonBody[SubContracts])
      .summary("Get sub contract addresses")
}
