// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.collection.immutable.ArraySeq

import org.apache.pekko.util.ByteString
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.persistence.queries.{ContractQueries, TokenQueries}
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.service.{BlockFlowClient, TokenService}
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, ContractId, TokenId}
import org.alephium.util.{AVector, U256}

class MetadataBytesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {
  private val contractId = ContractId.unsafe(Hash.unsafe(ByteString(Array.fill[Byte](32)(1))))
  private val token      = TokenId.from(contractId)
  private val address    = Address.contract(contractId)
  private val malformed  = ByteString(Array[Byte](0, -61, 40, -1))
  private val allBytes   = ByteString((0 to 255).map(_.toByte).toArray)

  private def contractResult(values: model.Val*): model.CallContractResult =
    model.CallContractSucceeded(
      AVector.from(values),
      0,
      AVector.empty,
      AVector.empty,
      AVector.empty,
      AVector.empty,
      AVector.empty
    )

  private def assertByteColumns(): Unit = {
    val types = exec(
      sql"""SELECT data_type FROM information_schema.columns
      WHERE table_schema = current_schema() AND
      ((table_name = 'fungible_token_metadata' AND column_name IN ('symbol', 'name'))
       OR (table_name = 'nft_metadata' AND column_name = 'token_uri')
       OR (table_name = 'nft_collection_metadata' AND column_name = 'collection_uri'))""".as[String]
    )
    val _ = types is Vector.fill(4)("bytea")
  }

  "Metadata bytes" should {
    "round trip arbitrary bytes through SQL and Slick and convert only at the API boundary" in {
      val fungible = BlockFlowClient
        .extractFungibleTokenMetadata(
          token,
          model.MultipleCallContractResult(
            AVector(
              contractResult(model.ValByteVec(malformed)),
              contractResult(model.ValByteVec(ByteString("λ token\\x00"))),
              contractResult(model.ValU256(U256.Zero))
            )
          )
        )
        .get
      val nft = BlockFlowClient
        .extractNFTMetadata(
          token,
          model.MultipleCallContractResult(
            AVector(
              contractResult(model.ValByteVec(allBytes)),
              contractResult(model.ValByteVec(contractId.bytes), model.ValU256(U256.One))
            )
          )
        )
        .get
      val collection = BlockFlowClient
        .extractNFTCollectionMetadata(
          address,
          model.MultipleCallContractResult(
            AVector(contractResult(model.ValByteVec(ByteString.empty)))
          )
        )
        .get
      exec(TokenQueries.insertFungibleTokenMetadata(fungible))
      exec(TokenQueries.insertNFTMetadata(nft))
      exec(ContractQueries.insertNFTCollectionMetadata(address, collection))
      assertByteColumns()
      exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(token))) is ArraySeq(fungible)
      exec(TokenQueries.listNFTMetadataQuery(ArraySeq(token))) is ArraySeq(nft)
      exec(TokenQueries.listNFTCollectionMetadataQuery(ArraySeq(address))) is ArraySeq(collection)
      exec(FungibleTokenMetadataSchema.table.result) is Vector(fungible)
      exec(NFTMetadataSchema.table.result) is Vector(nft)
      exec(NFTCollectionMetadataSchema.table.result) is Vector(collection)

      val apiToken = TokenService.listFungibleTokenMetadata(ArraySeq(token)).futureValue.head
      apiToken.symbol is "\u0000\ufffd(\ufffd"
      apiToken.name is "λ token\\x00"
      ujson.read(write(apiToken))("symbol").str is "\u0000\ufffd(\ufffd"
      TokenService.listNFTMetadata(ArraySeq(token)).futureValue.head.tokenUri is allBytes.utf8String
      TokenService.listNFTCollectionMetadata(ArraySeq(address)).futureValue.head.collectionUri is ""
      // API conversion must not replace the stored original with its lossy UTF-8 decoding.
      exec(TokenQueries.listNFTMetadataQuery(ArraySeq(token))).head.tokenUri is allBytes
      exec(Migrations.migration12.transactionally)
      exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(token))).head.symbol is malformed
    }

    "migrate legacy Unicode and literal backslashes without interpreting bytea syntax" in {
      exec(
        sqlu"""ALTER TABLE fungible_token_metadata
        ALTER COLUMN symbol TYPE text USING convert_from(symbol, 'UTF8'),
        ALTER COLUMN name TYPE text USING convert_from(name, 'UTF8');
        ALTER TABLE nft_metadata ALTER COLUMN token_uri TYPE text USING convert_from(token_uri, 'UTF8');
        ALTER TABLE nft_collection_metadata ALTER COLUMN collection_uri TYPE text USING convert_from(collection_uri, 'UTF8');"""
      )
      val symbol = "\\xdeadbeef"
      val name   = "名字 λ 😀"
      val uri    = "ipfs://local/\\000/雪"
      exec(sqlu"""INSERT INTO fungible_token_metadata VALUES ($token, $symbol, $name, 18)""")
      exec(sqlu"""INSERT INTO nft_metadata VALUES ($token, $uri, $contractId, 1)""")
      exec(sqlu"""INSERT INTO nft_collection_metadata VALUES ($address, $uri)""")
      exec(Migrations.migration12.transactionally)
      exec(Migrations.migration12.transactionally)
      assertByteColumns()
      val ft = exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(token))).head
      ft.symbol is ByteString(symbol)
      ft.name is ByteString(name)
      exec(TokenQueries.listNFTMetadataQuery(ArraySeq(token))).head.tokenUri is ByteString(uri)
      exec(
        TokenQueries.listNFTCollectionMetadataQuery(ArraySeq(address))
      ).head.collectionUri is ByteString(uri)
      TokenService.listFungibleTokenMetadata(ArraySeq(token)).futureValue.head.name is name
    }

    "resume a mixed text and bytea schema without rewriting binary values" in {
      exec(
        sqlu"""ALTER TABLE fungible_token_metadata ALTER COLUMN name TYPE text USING convert_from(name, 'UTF8')"""
      )
      val name = "already partially migrated"
      exec(sqlu"""INSERT INTO fungible_token_metadata VALUES ($token, $malformed, $name, 0)""")
      exec(Migrations.migration12.transactionally)
      assertByteColumns()
      val result = exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(token))).head
      result.symbol is malformed
      result.name is ByteString(name)
    }
  }
}
