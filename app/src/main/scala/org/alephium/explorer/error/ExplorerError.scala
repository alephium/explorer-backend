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

import org.postgresql.util.PSQLException
import sttp.model.Uri

import org.alephium.explorer.api.model.GroupIndex
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.protocol.model.{BlockHash, NetworkId}
import org.alephium.util.TimeStamp

/** All Explorer errors */
sealed trait ExplorerError extends Throwable

/** Configuration related errors */
sealed trait ConfigError extends ExplorerError

/** Errors that lead to JVM termination */
sealed trait FatalSystemExit extends ExplorerError

/** Application runtime errors */
sealed trait RuntimeError extends Throwable

//scalastyle:off null
@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object ExplorerError {

  /******** Group: [[FatalSystemExit]] ********/
  final case class UnreachableNode(cause: Throwable)
      extends Exception(s"Could not reach node.", cause)
      with FatalSystemExit

  final case class NodeApiError(message: String)
      extends Exception(s"Error on node api: $message.")
      with FatalSystemExit

  final case class ChainIdMismatch(remote: NetworkId, local: NetworkId)
      extends Exception(s"Chain id mismatch: $remote (remote) vs $local (local)")
      with FatalSystemExit

  final case class PeersNotFound(blockFlowUri: Uri)
      extends Exception(s"Peers not found. blockFlowUri: $blockFlowUri")
      with FatalSystemExit

  final case class InvalidChainGroupNumPerBroker(groupNumPerBroker: Int)
      extends Exception(
        s"SelfClique.groupNumPerBroker ($groupNumPerBroker) cannot be less or equal to zero")
      with FatalSystemExit

  final case class InvalidProtocolInput(error: String)
      extends Exception(s"Cannot decode protocol input: $error")
      with FatalSystemExit

  final case class RemoteTimeStampIsBeforeLocal(localTs: TimeStamp, remoteTs: TimeStamp)
      extends Exception(
        s"Max remote timestamp ($remoteTs) cannot be be before local timestamp ($localTs)")
      with FatalSystemExit

  final case class DatabaseError(cause: PSQLException)
      extends Exception(s"Database error.", cause)
      with FatalSystemExit

  /******** Group: [[ConfigError]] ********/
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

  final case class InvalidBootMode(mode: String)
      extends Exception(
        s"Invalid boot-mode: $mode. Valid modes are: ${BootMode.all.map(_.productPrefix).mkString(", ")}.")
      with ConfigError

  object BlocksInDifferentChains {

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    @inline def apply(parent: BlockHash,
                      parentChainFrom: GroupIndex,
                      parentChainTo: GroupIndex,
                      child: BlockEntity): BlocksInDifferentChains =
      new BlocksInDifferentChains(
        parent          = parent,
        parentChainFrom = parentChainFrom,
        parentChainTo   = parentChainTo,
        child           = child.hash,
        childChainFrom  = child.chainFrom,
        childChainTo    = child.chainTo
      )
  }

  /******** Group: [[RuntimeError]] ********/
  final case class BlocksInDifferentChains(parent: BlockHash,
                                           parentChainFrom: GroupIndex,
                                           parentChainTo: GroupIndex,
                                           child: BlockHash,
                                           childChainFrom: GroupIndex,
                                           childChainTo: GroupIndex)
      extends Exception(
        s"Parent ($parent) and child ($child) blocks belongs to different chains. " +
          s"ParentChain: ($parentChainFrom, $parentChainTo). ChildChain: ($childChainFrom, $childChainTo)")
      with RuntimeError
}
