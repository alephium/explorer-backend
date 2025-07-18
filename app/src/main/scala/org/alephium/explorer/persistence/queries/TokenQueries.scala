// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.TokenInfoEntity
import org.alephium.explorer.persistence.queries.result.TxByTokenQR
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.{TimeStamp, U256}

object TokenQueries extends StrictLogging {

  def getTokenBalanceAction(address: ApiAddress, token: TokenId)(implicit
      ec: ExecutionContext
  ): DBActionR[(U256, U256)] =
    getTokenBalanceUntilLockTime(
      address = address,
      token,
      lockTime = TimeStamp.now()
    ) map { case (total, locked) =>
      (total.getOrElse(U256.Zero), locked.getOrElse(U256.Zero))
    }

  def getTokenBalanceUntilLockTime(address: ApiAddress, token: TokenId, lockTime: TimeStamp)(
      implicit ec: ExecutionContext
  ): DBActionR[(Option[U256], Option[U256])] =
    sql"""
      SELECT sum(token_outputs.amount),
             sum(CASE
                     WHEN token_outputs.lock_time is NULL or token_outputs.lock_time < ${lockTime.millis} THEN 0
                     ELSE token_outputs.amount
                 END)
      FROM token_outputs
               LEFT JOIN inputs
                         ON token_outputs.key = inputs.output_ref_key
                             AND inputs.main_chain = true
      WHERE token_outputs.spent_finalized IS NULL
        AND token_outputs.#${addressColumn(address)} = $address
        AND token_outputs.token = $token
        AND token_outputs.main_chain = true
        AND inputs.block_hash IS NULL;
    """.asAS[(Option[U256], Option[U256])].exactlyOne

  def listTokensAction(
      pagination: Pagination,
      interfaceIdOpt: Option[StdInterfaceId]
  ): DBActionSR[TokenInfoEntity] = {
    val query = interfaceIdOpt match {
      case Some(id) =>
        sql"""
      SELECT token, last_used, category, interface_id
      FROM token_info
      WHERE interface_id = $id
      ORDER BY last_used DESC
    """
      case None =>
        sql"""
      SELECT token, last_used, category, interface_id
      FROM token_info
      ORDER BY last_used DESC
      """
    }

    query
      .paginate(pagination)
      .asASE[TokenInfoEntity](tokenInfoGetResult)

  }

  def getTransactionsByToken(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- listTokenTransactionsAction(token, pagination)
      txs        <- TransactionQueries.getTransactions(txHashesTs.map(_.toTxByAddressQR))
    } yield txs
  }

  def getAddressesByToken(token: TokenId, pagination: Pagination): DBActionR[ArraySeq[Address]] = {
    sql"""
      SELECT DISTINCT address
      FROM token_tx_per_addresses
      WHERE token = $token
    """
      .paginate(pagination)
      .asAS[Address]
  }

  def listTokenTransactionsAction(
      token: TokenId,
      pagination: Pagination
  ): DBActionSR[TxByTokenQR] = {
    sql"""
      SELECT #${TxByTokenQR.selectFields}
      FROM transaction_per_token
      WHERE main_chain = true
      AND token = $token
      ORDER BY block_timestamp DESC, tx_order
    """
      .paginate(pagination)
      .asAS[TxByTokenQR]
  }

  def listAddressTokensAction(
      address: ApiAddress,
      pagination: Pagination
  ): DBActionSR[TokenId] =
    sql"""
      SELECT DISTINCT token
      FROM token_tx_per_addresses
      WHERE #${addressColumn(address)} = $address
      AND main_chain = true
    """
      .paginate(pagination)
      .asAS[TokenId]

  def getTokenTransactionsByAddress(
      address: ApiAddress,
      token: TokenId,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTokenTxHashesByAddressQuery(address, token, pagination)
      txs        <- TransactionQueries.getTransactions(txHashesTs.map(_.toTxByAddressQR))
    } yield txs
  }

  def getTokenTxHashesByAddressQuery(
      address: ApiAddress,
      token: TokenId,
      pagination: Pagination
  ): DBActionSR[TxByTokenQR] = {
    sql"""
      SELECT #${distinct(address)} #${TxByTokenQR.selectFields}
      FROM token_tx_per_addresses
      WHERE main_chain = true
      AND #${addressColumn(address)} = $address
      AND token = $token
      ORDER BY block_timestamp DESC, tx_order
    """
      .paginate(pagination)
      .asAS[TxByTokenQR]
  }

  def listAddressTokensWithBalanceAction(address: ApiAddress, pagination: Pagination)(implicit
      ec: ExecutionContext
  ): DBActionSR[(TokenId, U256, U256)] =
    listAddressTokensWithBalanceUntilLockTime(address, TimeStamp.now(), pagination).map(_.map {
      case (token, total, locked) =>
        (token, total.getOrElse(U256.Zero), locked.getOrElse(U256.Zero))
    })

  def listAddressTokensWithBalanceUntilLockTime(
      address: ApiAddress,
      lockTime: TimeStamp,
      pagination: Pagination
  ): DBActionSR[(TokenId, Option[U256], Option[U256])] =
    sql"""
      SELECT
        token_outputs.token,
        sum(token_outputs.amount),
        sum(CASE
                WHEN token_outputs.lock_time is NULL or token_outputs.lock_time < ${lockTime.millis} THEN 0
                ELSE token_outputs.amount
            END)
      FROM token_outputs
               LEFT JOIN inputs
                         ON token_outputs.key = inputs.output_ref_key
                             AND inputs.main_chain = true
      WHERE token_outputs.spent_finalized IS NULL
        AND token_outputs.#${addressColumn(address)} = $address
        AND token_outputs.main_chain = true
        AND inputs.block_hash IS NULL
      GROUP BY token_outputs.token
    """
      .paginate(pagination)
      .asAS[(TokenId, Option[U256], Option[U256])]

  def insertFungibleTokenMetadata(
      metadata: FungibleTokenMetadata
  ): DBActionW[Int] = {
    sqlu"""
      INSERT INTO fungible_token_metadata (
        "token",
        "symbol",
        "name",
        "decimals"
        )
      VALUES (${metadata.id},${metadata.symbol},${metadata.name},${metadata.decimals})
      ON CONFLICT
      DO NOTHING
    """
  }

  def listFungibleTokenMetadataQuery(
      tokens: ArraySeq[TokenId]
  ): DBActionW[ArraySeq[FungibleTokenMetadata]] = {
    if (tokens.nonEmpty) {
      val params = paramPlaceholder(1, tokens.size)

      val query =
        s"""
          SELECT *
          FROM fungible_token_metadata
          WHERE token IN $params
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          tokens foreach { token =>
            params >> token
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[FungibleTokenMetadata](fungibleTokenMetadataGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def listTokenInfosQuery(
      tokens: ArraySeq[TokenId]
  ): DBActionW[ArraySeq[TokenInfoEntity]] = {
    if (tokens.nonEmpty) {
      val params = paramPlaceholder(1, tokens.size)

      val query =
        s"""
          SELECT token, last_used, category, interface_id
          FROM token_info
          WHERE token IN $params
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          tokens foreach { token =>
            params >> token
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[TokenInfoEntity](tokenInfoGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def listNFTMetadataQuery(
      tokens: ArraySeq[TokenId]
  ): DBActionW[ArraySeq[NFTMetadata]] = {
    if (tokens.nonEmpty) {
      val params = paramPlaceholder(1, tokens.size)

      val query =
        s"""
          SELECT *
          FROM nft_metadata
          WHERE token IN $params
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          tokens foreach { token =>
            params >> token
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[NFTMetadata](nftMetadataGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def listNFTCollectionMetadataQuery(
      addresses: ArraySeq[Address.Contract]
  ): DBActionW[ArraySeq[NFTCollectionMetadata]] = {
    if (addresses.nonEmpty) {
      val params = paramPlaceholder(1, addresses.size)

      val query =
        s"""
          SELECT *
          FROM nft_collection_metadata
          WHERE contract IN $params
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          addresses foreach { contract =>
            params >> contract
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[NFTCollectionMetadata](nftCollectionMetadataGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def insertNFTMetadata(
      metadata: NFTMetadata
  ): DBActionW[Int] = {
    sqlu"""
      INSERT INTO nft_metadata (
        "token",
        "token_uri",
        "collection_id",
        "nft_index"
        )
      VALUES (${metadata.id},${metadata.tokenUri},${metadata.collectionId},${metadata.nftIndex})
      ON CONFLICT
      DO NOTHING
    """
  }

  def listTokenWithoutInterfaceIdQuery(): DBActionW[ArraySeq[TokenId]] = {
    sql"""
      SELECT token
      FROM token_info
      WHERE interface_id IS NULL
    """.asAS[TokenId]
  }

  def updateTokenInterfaceId(token: TokenId, interfaceId: StdInterfaceId): DBActionW[Int] = {
    sqlu"""
      UPDATE token_info
      SET interface_id = $interfaceId, category = ${interfaceId.category}
      WHERE token = $token
    """
  }

  def countFungibles()(implicit
      ec: ExecutionContext
  ): DBActionR[Int] = {
    sql"""
      SELECT count(*)
      FROM token_info
      WHERE interface_id = '0001'
    """.asAS[Int].exactlyOne
  }

  def countNFT()(implicit
      ec: ExecutionContext
  ): DBActionR[Int] = {
    sql"""
      SELECT count(*)
      FROM token_info
      WHERE interface_id = '0003' OR interface_id = '000301'
    """.asAS[Int].exactlyOne
  }
}
