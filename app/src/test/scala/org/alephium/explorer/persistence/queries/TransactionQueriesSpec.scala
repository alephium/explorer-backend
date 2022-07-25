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

import scala.concurrent.ExecutionContext
import scala.util.Random

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumSpec, Generators, Hash}
import org.alephium.explorer.GenModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.result._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.service.FinalizerService
import org.alephium.explorer.util.SlickTestUtil._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

class TransactionQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with Generators
    with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  implicit val executionContext: ExecutionContext = ExecutionContext.global

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

    run(OutputSchema.table ++= Seq(output1, output2, output3, output4)).futureValue

    val (total, locked) = run(TransactionQueries.getBalanceAction(address)).futureValue

    total is ALPH.alph(10)
    locked is ALPH.alph(7)

  }

  "get balance should only return unpent outputs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val input1  = input(output2.hint, output2.key)

    run(OutputSchema.table ++= Seq(output1, output2)).futureValue
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

    run(OutputSchema.table ++= Seq(output1, output2)).futureValue
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

    val outputs = Seq(output1, output2, output3, output4)
    val inputs  = Seq(input1, input2, input3)
    run(TransactionQueries.insertAll(Seq.empty, outputs, inputs)).futureValue
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

    val outputs = Seq(output1)
    val inputs  = Seq(input1, input2)

    run(TransactionQueries.insertAll(Seq.empty, outputs, inputs)).futureValue
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

    val outputs = Seq(output1, output2, output3, output4)
    val inputs  = Seq(input1, input2, input3)

    run(TransactionQueries.insertAll(Seq.empty, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue

    val hashesSQLNoJoin =
      run(TransactionQueries.getTxHashesByAddressQuerySQLNoJoin(address, 0, 10)).futureValue

    val expected = Seq(
      TxByAddressQR(output1.txHash, output1.blockHash, output1.timestamp, 0),
      TxByAddressQR(output2.txHash, output2.blockHash, output2.timestamp, 0),
      TxByAddressQR(input1.txHash, input1.blockHash, input1.timestamp, 0)
    ).sortBy(_.blockTimestamp).reverse

    hashesSQLNoJoin is expected.toVector
  }

  "outpus for txs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None)
    val output4 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output2.hint, output2.key)
    val input2  = input(output3.hint, output3.key).copy(mainChain = false)

    val outputs = Seq(output1, output2, output3, output4)
    val inputs  = Seq(input1, input2)

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

    val expected = Seq(
      res(output1, None),
      res(output2, Some(input1)),
      res(output3, None)
    ).sortBy(_.txHash.toString())

    run(outputsFromTxsSQL(txHashes)).futureValue.sortBy(_.txHash.toString()) is expected.toVector
  }

  "inputs for txs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output1.hint, output1.key)
    val input2  = input(output2.hint, output2.key)
    val input3  = input(output3.hint, output3.key)

    val inputs  = Seq(input1, input2)
    val outputs = Seq(output1, output2)

    run(OutputSchema.table ++= (outputs :+ output3)).futureValue
    run(InputSchema.table ++= (inputs :+ input3)).futureValue

    val txHashes = Seq(input1.txHash, input2.txHash)

    val expected = inputs.zip(outputs).map {
      case (input, output) =>
        InputsFromTxQR(
          txHash       = input.txHash,
          inputOrder   = input.inputOrder,
          hint         = input.hint,
          outputRefKey = input.outputRefKey,
          unlockScript = input.unlockScript,
          address      = Some(output.address),
          amount       = Some(output.amount),
          token        = output.tokens
        )
    }

    run(inputsFromTxsSQL(txHashes)).futureValue is expected.toVector
  }

  "get tx by address" in new Fixture {
    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output2.hint, output2.key)
    val input2  = input(output3.hint, output3.key).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)
      .copy(txHash = input1.txHash, blockHash = input1.blockHash, timestamp = input1.timestamp)

    val outputs      = Seq(output1, output2, output3, output4)
    val inputs       = Seq(input1, input2)
    val transactions = outputs.map(transaction)

    run(TransactionQueries.insertAll(transactions, outputs, inputs)).futureValue
    run(InputUpdateQueries.updateInputs()).futureValue
    val from = TimeStamp.zero
    val to   = timestampMaxValue
    FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)).futureValue

    def tx(output: OutputEntity, spent: Option[Transaction.Hash], inputs: Seq[Input]) = {
      Transaction(
        output.txHash,
        output.blockHash,
        output.timestamp,
        inputs,
        Seq(output.toApi(spent)),
        1,
        ALPH.alph(1)
      )
    }

    val txsSQL =
      run(TransactionQueries.getTransactionsByAddressSQL(address, Pagination.unsafe(0, 10))).futureValue

    val txsNoJoin =
      run(TransactionQueries.getTransactionsByAddressNoJoin(address, Pagination.unsafe(0, 10))).futureValue

    val expected = Seq(
      tx(output1, None, Seq.empty),
      tx(output2, Some(input1.txHash), Seq.empty),
      tx(output4, None, Seq(input1.toApi(output2)))
    ).sortBy(_.timestamp).reverse

    txsSQL.size is 3
    txsSQL is expected

    txsSQL is txsNoJoin
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
      None
    )

    val output1 =
      output(address, ALPH.alph(1), None).copy(txHash = tx1.hash, blockHash = tx1.blockHash)
    val input1 = input(output1.hint, output1.key).copy(txHash = tx2.hash, blockHash = tx2.blockHash)
    val input2 = input(output1.hint, output1.key).copy(txHash = tx2.hash).copy(mainChain = false)

    run(OutputSchema.table += output1).futureValue
    run(InputSchema.table ++= Seq(input1, input2)).futureValue
    run(TransactionSchema.table ++= Seq(txEntity1)).futureValue

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
    run(TransactionQueries.getTransactionsByAddressSQL(address, Pagination.unsafe(0, 10))).futureValue is Seq.empty
  }

  "get total number of main transactions" in new Fixture {

    val tx1 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx2 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx3 = transactionEntityGen().sample.get.copy(mainChain = false)

    run(TransactionSchema.table ++= Seq(tx1, tx2, tx3)).futureValue

    val total = run(TransactionQueries.mainTransactions.length.result).futureValue
    total is 2
  }

  "index 'txs_pk'" should {
    "get used" when {
      "accessing column hash" in {
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
            run(TransactionQueries.filterExistingAddresses(allAddresses)).futureValue

          //actual address should contain all of existingAddresses
          actualExisting should contain theSameElementsAs existingAddresses

          //run the boolean query
          val booleanExistingResult =
            run(TransactionQueries.areAddressesActiveAction(allAddresses)).futureValue

          //boolean query should output results in the same order as the input addresses
          (allAddresses.map(actualExisting.contains): Seq[Boolean]) is booleanExistingResult

          //check the boolean query with the existing concurrent query
          run(TransactionQueries.areAddressesActiveAction(allAddresses)).futureValue is booleanExistingResult
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
                        None)
    }
  }
}
