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

package org.alephium.explorer.error

import scala.concurrent.duration.FiniteDuration

import akka.http.scaladsl.model.Uri

import org.alephium.explorer.config.ApplicationConfig
import org.alephium.protocol.model.NetworkId
import org.alephium.serde.SerdeError
import org.alephium.util.TimeStamp

/** All Explorer errors */
sealed trait ExplorerError extends Throwable

/** Configuration related errors */
sealed trait ConfigError extends ExplorerError

/** Errors that lead to JVM termination */
sealed trait FatalSystemExit extends ExplorerError

object ExplorerError {

  /******** Group: [[FatalSystemExit]] ********/
  final case class FailedToFetchSelfClique(remoteMessage: String)
      extends Exception(s"Could not fetch self-clique: $remoteMessage.")
      with FatalSystemExit

  final case class ChainIdMismatch(remote: NetworkId, local: NetworkId)
      extends Exception(s"Chain id mismatch: $remote (remote) vs $local (local)")
      with FatalSystemExit

  final case class ImpossibleToFetchNetworkType(err: String)
      extends Exception(s"Impossible to fetch network type: $err")
      with FatalSystemExit

  final case class PeersNotFound(blockFlowUri: Uri)
      extends Exception(s"Peers not found. blockFlowUri: $blockFlowUri")
      with FatalSystemExit

  final case class InvalidMigrationVersion(error: SerdeError)
      extends Exception("Invalid migration version", error)
      with FatalSystemExit

  final case class InvalidMaxRemoveTimeStamp(remote: TimeStamp, local: TimeStamp)
      extends Exception(
        s"Max remote timestamp ('$remote') should not be before local timestamp ('$local')")
      with FatalSystemExit

  /******** Group: [[ConfigError]] ********/
  final case class InvalidApplicationConfig(field: String, cause: Throwable)
      extends Exception(s"Invalid ${ApplicationConfig.productPrefix}: $field", cause)
      with ConfigError

  final case class EmptyApplicationConfig()
      extends Exception(s"Empty ${ApplicationConfig.productPrefix}")
      with ConfigError

  final case class InvalidGroupNumber(groupNum: Int)
      extends Exception(s"Invalid groupNum: $groupNum. It should be > 0")
      with ConfigError

  final case class InvalidPortNumber(portNumber: Int)
      extends Exception(s"Invalid portNumber: $portNumber. It should be >= 1 and <= 65,535")
      with ConfigError

  final case class InvalidHost(host: String, cause: Throwable)
      extends Exception(s"Invalid host: $host", cause)
      with ConfigError

  final case class InvalidNetworkId(networkId: Int)
      extends Exception(s"Invalid networkId: $networkId")
      with ConfigError

  final case class InvalidApiKey(message: String)
      extends Exception(s"Invalid apiKey: $message")
      with ConfigError

  final case class InvalidSyncPeriod(syncPeriod: FiniteDuration)
      extends Exception(s"Invalid syncPeriod: ${syncPeriod.toString}. Sync-period must be > 0.")
      with ConfigError

}
