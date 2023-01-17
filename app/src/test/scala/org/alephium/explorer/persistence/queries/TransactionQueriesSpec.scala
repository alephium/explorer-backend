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

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.util.Random

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, Hash, TestQueries}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.result._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.service.FinalizerService
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.TransactionId
import org.alephium.util.{Duration, TimeStamp, U256}

class TransactionQueriesSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {

  "compute locked balance when empty" in new Fixture {
    val balance = run(TransactionQueries.getBalanceAction(address)).futureValue
    balance is ((U256.Zero, U256.Zero))
  }

  "compute locked balance" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 =
      output(address, ALPH.alph(2), Some(TimeStamp.now().minusUnsafe(Duration.ofMinutesUnsafe(10))))
    val output3 = output(address, ALPH.alph(3), Some(TimeStamp.now().plusMinutesUnsafe(10)))
    val output4 = output(address, ALPH.alph(4), Some(TimeStamp.now().plusMinutesUnsafe(10)))

    run(OutputSchema.table ++= ArraySeq(output1, output2, output3, output4)).futureValue

    val (total, locked) = run(TransactionQueries.getBalanceAction(address)).futureValue

    total is ALPH.alph(10)
    locked is ALPH.alph(7)

  }

  "get balance should only return unpent outputs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val input1  = input(output2.hint, output2.key)

    run(OutputSchema.table ++= ArraySeq(output1, output2)).futureValue
    run(InputSchema.table += input1).futureValue

    val (total, _) = run(TransactionQueries.getBalanceAction(address)).futureValue

    total is ALPH.alph(1)

    val from = TimeStamp.zero
    val to   = timestampMaxValue
    FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)).futureValue

    val (totalFinalized, _) = run(TransactionQueries.getBalanceAction(address)).futureValue

    totalFinalized is ALPH.alph(1)
  }

  "get balance should only take main chain outputs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None).copy(mainChain = false)
    val input1  = input(output2.hint, output2.key).copy(mainChain = false)

    run(OutputSchema.table ++= ArraySeq(output1, output2)).futureValue
    run(InputSchema.table += input1).futureValue

    val (total, _) = run(TransactionQueries.getBalanceAction(address)).futureValue

    total is ALPH.alph(1)
  }

  "txs count" in new Fixture {
    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)

    val input1 = input(output2.hint, output2.key)
    val input2 = input(output3.hint, output3.key).copy(mainChain = false)
    val input3 = input(output4.hint, output4.key)

    val outputs = ArraySeq(output1, output2, output3, output4)
    val inputs  = ArraySeq(input1, input2, input3)
    run(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue

    val totalSQLNoJoin =
      run(TransactionQueries.countAddressTransactionsSQLNoJoin(address)).futureValue.head

    //tx of output1, output2 and input1
    totalSQLNoJoin is 3
  }

  "update inputs when corresponding output is finally inserted" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)

    val input1 = input(output1.hint, output1.key)
    val input2 = input(output2.hint, output2.key)

    val outputs = ArraySeq(output1)
    val inputs  = ArraySeq(input1, input2)

    run(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue

    run(TransactionQueries.countAddressTransactionsSQLNoJoin(address)).futureValue.head is 2

    run(OutputSchema.table += output2).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue

    run(TransactionQueries.countAddressTransactionsSQLNoJoin(address)).futureValue.head is 3
  }

  "get tx hashes by address" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)
    val input1  = input(output2.hint, output2.key)
    val input2  = input(output3.hint, output3.key).copy(mainChain = false)
    val input3  = input(output4.hint, output4.key)

    val outputs = ArraySeq(output1, output2, output3, output4)
    val inputs  = ArraySeq(input1, input2, input3)

    run(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue

    val hashesSQLNoJoin =
      run(TransactionQueries.getTxHashesByAddressQuerySQLNoJoin(address, Pagination.unsafe(1, 10))).futureValue

    val expected = ArraySeq(
      TxByAddressQR(output1.txHash, output1.blockHash, output1.timestamp, 0, false),
      TxByAddressQR(output2.txHash, output2.blockHash, output2.timestamp, 0, false),
      TxByAddressQR(input1.txHash, input1.blockHash, input1.timestamp, 0, false)
    ).sortBy(_.blockTimestamp).reverse

    hashesSQLNoJoin is expected
  }

  "outpus for txs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None)
    val output4 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output2.hint, output2.key)
    val input2  = input(output3.hint, output3.key).copy(mainChain = false)

    val outputs = ArraySeq(output1, output2, output3, output4)
    val inputs  = ArraySeq(input1, input2)

    run(OutputSchema.table ++= outputs).futureValue
    run(InputSchema.table ++= inputs).futureValue

    val txHashes = outputs.map(_.txHash)

    def res(output: OutputEntity, input: Option[InputEntity]) =
      OutputsFromTxQR(
        output.txHash,
        output.outputOrder,
        output.outputType,
        output.hint,
        output.key,
        output.amount,
        output.address,
        output.tokens,
        output.lockTime,
        output.message,
        input.map(_.txHash)
      )

    val expected = ArraySeq(
      res(output1, None),
      res(output2, Some(input1)),
      res(output3, None)
    ).sortBy(_.txHash.toString())

    run(outputsFromTxsSQL(txHashes)).futureValue.sortBy(_.txHash.toString()) is expected
  }

  "inputs for txs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output1.hint, output1.key)
    val input2  = input(output2.hint, output2.key)
    val input3  = input(output3.hint, output3.key)

    val inputs  = ArraySeq(input1, input2)
    val outputs = ArraySeq(output1, output2)

    run(OutputSchema.table ++= (outputs :+ output3)).futureValue
    run(InputSchema.table ++= (inputs :+ input3)).futureValue

    val txHashes = ArraySeq(input1.txHash, input2.txHash)

    val expected = inputs.zip(outputs).map {
      case (input, output) =>
        InputsFromTxQR(
          txHash       = input.txHash,
          inputOrder   = input.inputOrder,
          hint         = input.hint,
          outputRefKey = input.outputRefKey,
          unlockScript = input.unlockScript,
          txHashRef    = Some(output.txHash),
          address      = Some(output.address),
          amount       = Some(output.amount),
          token        = output.tokens
        )
    }

    run(inputsFromTxsSQL(txHashes)).futureValue is expected
  }

  "get tx by address" in new Fixture {
    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output2.hint, output2.key)
    val input2  = input(output3.hint, output3.key).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)
      .copy(txHash = input1.txHash, blockHash = input1.blockHash, timestamp = input1.timestamp)

    val outputs      = ArraySeq(output1, output2, output3, output4)
    val inputs       = ArraySeq(input1, input2)
    val transactions = outputs.map(transaction)

    run(TransactionQueries.insertAll(transactions, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue
    val from = TimeStamp.zero
    val to   = timestampMaxValue
    FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)).futureValue

    def tx(output: OutputEntity, spent: Option[TransactionId], inputs: ArraySeq[Input]) = {
      Transaction(
        output.txHash,
        output.blockHash,
        output.timestamp,
        inputs,
        ArraySeq(output.toApi(spent)),
        1,
        ALPH.alph(1),
        coinbase = false
      )
    }

    val txsSQL =
      run(TransactionQueries.getTransactionsByAddressSQL(address, Pagination.unsafe(1, 10))).futureValue

    val txsNoJoin =
      run(TransactionQueries.getTransactionsByAddressNoJoin(address, Pagination.unsafe(1, 10))).futureValue

    val expected = ArraySeq(
      tx(output1, None, ArraySeq.empty),
      tx(output2, Some(input1.txHash), ArraySeq.empty),
      tx(output4, None, ArraySeq(input1.toApi(output2)))
    ).sortBy(_.timestamp).reverse

    txsSQL.size is 3
    txsSQL should contain allElementsOf expected

    txsSQL should contain allElementsOf txsNoJoin
  }

  "output's spent info should only take the input from the main chain " in new Fixture {

    val tx1 = transactionGen.sample.get
    val tx2 = transactionGen.sample.get

    val txEntity1 = TransactionEntity(
      tx1.hash,
      tx1.blockHash,
      tx1.timestamp,
      chainFrom,
      chainTo,
      tx1.gasAmount,
      tx1.gasPrice,
      0,
      true,
      true,
      None,
      None,
      coinbase = false
    )

    val output1 =
      output(address, ALPH.alph(1), None).copy(txHash = tx1.hash, blockHash = tx1.blockHash)
    val input1 = input(output1.hint, output1.key).copy(txHash = tx2.hash, blockHash = tx2.blockHash)
    val input2 = input(output1.hint, output1.key).copy(txHash = tx2.hash).copy(mainChain = false)

    run(OutputSchema.table += output1).futureValue
    run(InputSchema.table ++= ArraySeq(input1, input2)).futureValue
    run(TransactionSchema.table ++= ArraySeq(txEntity1)).futureValue

    val tx = run(TransactionQueries.getTransactionAction(tx1.hash)).futureValue.get

    tx.outputs.size is 1 // was 2 in v1.4.1
  }

  "insert and ignore transactions" in new Fixture {

    forAll(Gen.listOf(updatedTransactionEntityGen())) { transactions =>
      run(TransactionSchema.table.delete).futureValue

      val existingTransactions = transactions.map(_._1)
      val updatedTransactions  = transactions.map(_._2)

      //insert
      run(TransactionQueries.insertTransactions(existingTransactions)).futureValue is existingTransactions.size
      run(TransactionSchema.table.result).futureValue should contain allElementsOf existingTransactions

      //ignore
      run(TransactionQueries.insertTransactions(updatedTransactions)).futureValue is 0
      run(TransactionSchema.table.result).futureValue should contain allElementsOf existingTransactions
    }
  }

  //https://github.com/alephium/explorer-backend/issues/174
  "return an empty list when not transactions are found - Isssue 174" in new Fixture {
    run(TransactionQueries.getTransactionsByAddressSQL(address, Pagination.unsafe(1, 10))).futureValue is ArraySeq.empty
  }

  "get total number of main transactions" in new Fixture {

    val tx1 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx2 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx3 = transactionEntityGen().sample.get.copy(mainChain = false)

    run(TransactionSchema.table ++= ArraySeq(tx1, tx2, tx3)).futureValue

    val total = run(TransactionQueries.mainTransactions.length.result).futureValue
    total is 2
  }

  "index 'txs_pk'" should {
    "get used" when {
      "accessing column hash" ignore {
        forAll(Gen.listOf(transactionEntityGen())) { transactions =>
          run(TransactionSchema.table.delete).futureValue
          run(TransactionSchema.table ++= transactions).futureValue

          transactions foreach { transaction =>
            val query =
              sql"""
                   |SELECT *
                   |FROM #${TransactionSchema.name}
                   |where hash = ${transaction.hash}
                   |""".stripMargin

            val explain = run(query.explain()).futureValue.mkString("\n")

            explain should include("Index Scan on txs_pk")
          }
        }
      }
    }
  }

  "filterExistingAddresses & areAddressesActiveActionNonConcurrent" should {
    "return address that exist in DB" in {
      //generate two lists: Left to be persisted/existing & right as non-existing.
      forAll(Gen.listOf(genTransactionPerAddressEntity()),
             Gen.listOf(genTransactionPerAddressEntity())) {
        case (existing, nonExisting) =>
          //clear the table
          run(TransactionPerAddressSchema.table.delete).futureValue
          //persist existing entities
          run(TransactionPerAddressSchema.table ++= existing).futureValue

          //join existing and nonExisting entities and shuffle
          val allEntities  = Random.shuffle(existing ++ nonExisting)
          val allAddresses = allEntities.map(_.address)

          //get persisted entities to assert against actual query results
          val existingAddresses = existing.map(_.address)

          //fetch actual persisted addresses that exists
          val actualExisting =
            run(TransactionQueries.filterExistingAddresses(allAddresses.toSet)).futureValue

          //actual address should contain all of existingAddresses
          actualExisting should contain theSameElementsAs existingAddresses

          //run the boolean query
          val booleanExistingResult =
            run(TransactionQueries.areAddressesActiveAction(allAddresses)).futureValue

          //boolean query should output results in the same order as the input addresses
          (allAddresses.map(actualExisting.contains): ArraySeq[Boolean]) is booleanExistingResult

          //check the boolean query with the existing concurrent query
          run(TransactionQueries.areAddressesActiveAction(allAddresses)).futureValue is booleanExistingResult
      }
    }
  }

  "getTxHashesByAddressQuerySQLNoJoinTimeRanged" should {
    "return empty" when {
      "database is empty" in {
        forAll(addressGen, timestampGen, timestampGen, Gen.posNum[Int], Gen.posNum[Int]) {
          (address, fromTime, toTime, page, limit) =>
            val query =
              TransactionQueries.getTxHashesByAddressQuerySQLNoJoinTimeRanged(
                address,
                fromTime,
                toTime,
                Pagination.unsafe(page, limit))

            run(query).futureValue.size is 0
        }
      }
    }

    "return transactions_per_address within the range" when {
      "there is exactly one transaction per address (each address and timestamp belong to only one transaction)" in {
        forAll(Gen.listOf(genTransactionPerAddressEntity(mainChain = Gen.const(true)))) {
          entities =>
            //clear table and insert entities
            run(TestQueries.clearAndInsert(entities)).futureValue

            entities foreach { entity =>
              //run the query for each entity and expect that same entity to be returned
              val query =
                TransactionQueries
                  .getTxHashesByAddressQuerySQLNoJoinTimeRanged(address  = entity.address,
                                                                fromTime = entity.timestamp,
                                                                toTime   = entity.timestamp,
                                                                pagination =
                                                                  Pagination.unsafe(1,
                                                                                    Int.MaxValue))

              val actualResult = run(query).futureValue

              val expectedResult =
                TxByAddressQR(entity.hash,
                              entity.blockHash,
                              entity.timestamp,
                              entity.txOrder,
                              entity.coinbase)

              actualResult should contain only expectedResult
            }
        }
      }

      "there are multiple transactions per address" in {
        //a narrowed time range so there is overlap between transaction timestamps
        val timeStampGen =
          Gen.chooseNum(0L, 10L).map(TimeStamp.unsafe)

        val genTransactionsPerAddress =
          addressGen flatMap { address =>
            //for a single address generate multiple `TransactionPerAddressEntity`
            val entityGen =
              genTransactionPerAddressEntity(addressGen   = Gen.const(address),
                                             timestampGen = timeStampGen)

            Gen.listOf(entityGen)
          }

        forAll(genTransactionsPerAddress) { entities =>
          //clear table and insert entities
          run(TestQueries.clearAndInsert(entities)).futureValue

          //for each address run the query for randomly selected time-range and expect entities
          //only for that time-range to be returned
          entities.groupBy(_.address) foreach {
            case (address, entities) =>
              val minTime = entities.map(_.timestamp).min.millis //minimum time
              val maxTime = entities.map(_.timestamp).max.millis //maximum time

              //randomly select time range from the above minimum and maximum time range
              val timeRangeGen =
                for {
                  fromTime <- Gen.chooseNum(minTime, maxTime)
                  toTime   <- Gen.chooseNum(fromTime, maxTime)
                } yield (TimeStamp.unsafe(fromTime), TimeStamp.unsafe(toTime))

              //Run test on multiple randomly generated time-ranges on the same test data persisted
              forAll(timeRangeGen) {
                case (fromTime, toTime) =>
                  //run the query for the generated time-range
                  val query =
                    TransactionQueries
                      .getTxHashesByAddressQuerySQLNoJoinTimeRanged(
                        address    = address,
                        fromTime   = fromTime,
                        toTime     = toTime,
                        pagination = Pagination.unsafe(1, Int.MaxValue))

                  val actualResult = run(query).futureValue

                  //expect only main_chain entities within the queried time-range
                  val expectedEntities =
                    entities filter { entity =>
                      entity.mainChain && entity.timestamp >= fromTime && entity.timestamp <= toTime
                    }

                  //transform query result TxByAddressQR
                  val expectedResult =
                    expectedEntities map { entity =>
                      TxByAddressQR(entity.hash,
                                    entity.blockHash,
                                    entity.timestamp,
                                    entity.txOrder,
                                    entity.coinbase)
                    }

                  //only the main_chain entities within the time-range is expected
                  actualResult should contain theSameElementsAs expectedResult
              }
          }
        }
      }
    }
  }

  "getTxHashesByAddressesQuery" should {
    "return empty" when {
      "database is empty" in {
        forAll(Gen.listOf(addressGen), Gen.posNum[Int], Gen.posNum[Int]) {
          (addresses, page, limit) =>
            val query =
              TransactionQueries.getTxHashesByAddressesQuery(addresses,
                                                             Pagination.unsafe(page, limit))

            run(query).futureValue.size is 0
        }
      }
    }

    "return valid transactions_per_addresses" when {
      "the input contains valid and invalid addresses" in {
        forAll(Gen.listOf(genTransactionPerAddressEntity()), Gen.listOf(addressGen)) {
          case (entitiesToPersist, addressesNotPersisted) =>
            //clear table and insert entities
            run(TransactionPerAddressSchema.table.delete).futureValue
            run(TransactionPerAddressSchema.table ++= entitiesToPersist).futureValue

            //merge all addresses
            val allAddresses =
              entitiesToPersist.map(_.address) ++ addressesNotPersisted

            //shuffle all merged addresses
            val shuffledAddresses =
              Random.shuffle(allAddresses)

            //query shuffled addresses
            val query =
              TransactionQueries.getTxHashesByAddressesQuery(shuffledAddresses,
                                                             Pagination.unsafe(1, Int.MaxValue))

            //expect only main_chain and persisted address
            val expectedResult =
              entitiesToPersist.filter(_.mainChain) map { entity =>
                TxByAddressQR(entity.hash,
                              entity.blockHash,
                              entity.timestamp,
                              entity.txOrder,
                              entity.coinbase)
              }

            run(query).futureValue should contain theSameElementsAs expectedResult
        }
      }
    }
  }

  trait Fixture {

    val address = addressGen.sample.get
    def now     = TimeStamp.now().plusMinutesUnsafe(scala.util.Random.nextLong(240))

    val chainFrom = GroupIndex.unsafe(0)
    val chainTo   = GroupIndex.unsafe(0)

    def output(address: Address, amount: U256, lockTime: Option[TimeStamp]): OutputEntity =
      OutputEntity(
        blockEntryHashGen.sample.get,
        transactionHashGen.sample.get,
        now,
        outputTypeGen.sample.get,
        0,
        hashGen.sample.get,
        amount,
        address,
        Gen.option(tokensGen).sample.get,
        true,
        lockTime,
        Gen.option(bytesGen).sample.get,
        0,
        0,
        false,
        None
      )

    def input(hint: Int, outputRefKey: Hash): InputEntity =
      InputEntity(blockEntryHashGen.sample.get,
                  transactionHashGen.sample.get,
                  now,
                  hint,
                  outputRefKey,
                  None,
                  true,
                  0,
                  0,
                  None,
                  None,
                  None,
                  None)
    def transaction(output: OutputEntity): TransactionEntity = {
      TransactionEntity(output.txHash,
                        output.blockHash,
                        output.timestamp,
                        GroupIndex.unsafe(0),
                        GroupIndex.unsafe(1),
                        1,
                        ALPH.alph(1),
                        0,
                        true,
                        true,
                        None,
                        None,
                        coinbase = false)
    }
  }
}
