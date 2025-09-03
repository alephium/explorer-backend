// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.util.Random

import org.scalacheck.Gen
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.{AlephiumFutureSpec, TestQueries}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.config.Default.groupConfig
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.result._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.service.FinalizerService
import org.alephium.explorer.util.{AddressUtil, UtxoUtil}
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, GroupIndex, TransactionId}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.{Duration, TimeStamp, U256}

class TransactionQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  "compute locked balance when empty" in new Fixture {
    val balance =
      exec(TransactionQueries.getBalanceAction(address, lastFinalizedTime))
    balance is ((U256.Zero, U256.Zero))
  }

  "compute locked balance" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 =
      output(address, ALPH.alph(2), Some(TimeStamp.now().minusUnsafe(Duration.ofMinutesUnsafe(10))))
    val output3 = output(address, ALPH.alph(3), Some(TimeStamp.now().plusMinutesUnsafe(10)))
    val output4 = output(address, ALPH.alph(4), Some(TimeStamp.now().plusMinutesUnsafe(10)))

    exec(OutputSchema.table ++= ArraySeq(output1, output2, output3, output4))

    val (total, locked) =
      exec(TransactionQueries.getBalanceAction(address, lastFinalizedTime))

    total is ALPH.alph(10)
    locked is ALPH.alph(7)

  }

  "get balance should only return unspent outputs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val input1 = input(output2).copy(
      outputRefAddress = Some(address),
      outputRefGrouplessAddress = AddressUtil.convertToGrouplessAddress(address)
    )

    exec(OutputSchema.table ++= ArraySeq(output1, output2))
    exec(InputSchema.table += input1)

    val (total, _) =
      exec(TransactionQueries.getBalanceAction(address, lastFinalizedTime))

    total is ALPH.alph(1)

    val from = TimeStamp.zero
    val to   = timestampMaxValue
    FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)).futureValue

    val (totalFinalized, _) =
      exec(TransactionQueries.getBalanceAction(address, lastFinalizedTime))

    totalFinalized is ALPH.alph(1)
  }

  "get balance should only take main chain outputs" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None).copy(mainChain = false)
    val input1  = input(output2).copy(mainChain = false)

    exec(OutputSchema.table ++= ArraySeq(output1, output2))
    exec(InputSchema.table += input1)

    val (total, _) =
      exec(TransactionQueries.getBalanceAction(address, lastFinalizedTime))

    total is ALPH.alph(1)
  }

  "txs count" in new Fixture {
    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)

    val input1 = input(output2)
    val input2 = input(output3).copy(mainChain = false)
    val input3 = input(output4)

    val outputs = ArraySeq(output1, output2, output3, output4)
    val inputs  = ArraySeq(input1, input2, input3)
    exec(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs))
    exec(InputUpdateQueries.updateInputs())

    val total =
      exec(TransactionQueries.countAddressTransactions(address)).head

    // tx of output1, output2 and input1
    total is 3
  }

  "update inputs when corresponding output is finally inserted" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)

    val input1 = input(output1)
    val input2 = input(output2)

    val outputs = ArraySeq(output1)
    val inputs  = ArraySeq(input1, input2)

    exec(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs))
    exec(InputUpdateQueries.updateInputs())

    exec(TransactionQueries.countAddressTransactions(address)).head is 2

    exec(OutputSchema.table += output2)
    exec(InputUpdateQueries.updateInputs())

    exec(TransactionQueries.countAddressTransactions(address)).head is 3
  }

  "get tx hashes by address" in new Fixture {

    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)
    val input1  = input(output2)
    val input2  = input(output3).copy(mainChain = false)
    val input3  = input(output4)

    val outputs = ArraySeq(output1, output2, output3, output4)
    val inputs  = ArraySeq(input1, input2, input3)

    exec(TransactionQueries.insertAll(ArraySeq.empty, outputs, inputs))
    exec(InputUpdateQueries.updateInputs())

    val hashes =
      exec(
        TransactionQueries.getTxHashesByAddressQuery(address, Pagination.unsafe(1, 10))
      )

    val expected = ArraySeq(
      TxByAddressQR(output1.txHash, output1.blockHash, output1.timestamp, 0, false),
      TxByAddressQR(output2.txHash, output2.blockHash, output2.timestamp, 0, false),
      TxByAddressQR(input1.txHash, input1.blockHash, input1.timestamp, 0, false)
    ).sortBy(_.blockTimestamp).reverse

    hashes is expected
  }

  "get tx by address" in new Fixture {
    val output1 = output(address, ALPH.alph(1), None)
    val output2 = output(address, ALPH.alph(2), None)
    val output3 = output(address, ALPH.alph(3), None).copy(mainChain = false)
    val input1  = input(output2)
    val input2  = input(output3).copy(mainChain = false)
    val output4 = output(addressGen.sample.get, ALPH.alph(3), None)
      .copy(txHash = input1.txHash, blockHash = input1.blockHash, timestamp = input1.timestamp)

    val outputs      = ArraySeq(output1, output2, output3, output4)
    val inputs       = ArraySeq(input1, input2)
    val transactions = outputs.map(transaction)

    exec(TransactionQueries.insertAll(transactions, outputs, inputs))
    exec(InputUpdateQueries.updateInputs())
    val from = TimeStamp.zero
    val to   = timestampMaxValue
    FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)).futureValue

    val txs =
      exec(
        TransactionQueries.getTransactionsByAddress(address, Pagination.unsafe(1, 10))
      )

    val expected = ArraySeq(
      transaction(transactions(0), output1, None, ArraySeq.empty),
      transaction(transactions(1), output2, Some(input1.txHash), ArraySeq.empty),
      transaction(transactions(3), output4, None, ArraySeq(inputEntityToApi(input1, Some(output2))))
    ).sortBy(_.timestamp)

    txs.size is 3
    txs should contain allElementsOf expected
  }

  "output's spent info should only take the input from the main chain " in new Fixture {

    val tx1 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx2 = transactionEntityGen().sample.get

    val output1 =
      output(address, ALPH.alph(1), None).copy(txHash = tx1.hash, blockHash = tx1.blockHash)
    val input1 = input(output1).copy(txHash = tx2.hash, blockHash = tx2.blockHash)
    val input2 = input(output1).copy(txHash = tx2.hash).copy(mainChain = false)

    exec(OutputSchema.table += output1)
    exec(InputSchema.table ++= ArraySeq(input1, input2))
    exec(TransactionSchema.table ++= ArraySeq(tx1))

    val tx = exec(TransactionQueries.getTransactionAction(tx1.hash)).get

    tx.outputs.size is 1 // was 2 in v1.4.1
  }

  "insert and ignore transactions" in new Fixture {

    forAll(Gen.listOf(updatedTransactionEntityGen())) { transactions =>
      exec(TransactionSchema.table.delete)

      val existingTransactions = transactions.map(_._1)
      val updatedTransactions  = transactions.map(_._2)

      // insert
      exec(
        TransactionQueries.insertTransactions(existingTransactions)
      ) is existingTransactions.size
      exec(
        TransactionSchema.table.result
      ) should contain allElementsOf existingTransactions

      // ignore
      exec(TransactionQueries.insertTransactions(updatedTransactions)) is 0
      exec(
        TransactionSchema.table.result
      ) should contain allElementsOf existingTransactions
    }
  }

  // https://github.com/alephium/explorer-backend/issues/174
  "return an empty list when not transactions are found - Isssue 174" in new Fixture {
    exec(
      TransactionQueries.getTransactionsByAddress(address, Pagination.unsafe(1, 10))
    ) is ArraySeq.empty
  }

  "get total number of main transactions" in new Fixture {

    val tx1 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx2 = transactionEntityGen().sample.get.copy(mainChain = true)
    val tx3 = transactionEntityGen().sample.get.copy(mainChain = false)

    exec(TransactionSchema.table ++= ArraySeq(tx1, tx2, tx3))

    val total = exec(TransactionQueries.mainTransactions.length.result)
    total is 2
  }

  "index 'txs_pk'" should {
    "get used" when {
      "accessing column hash" ignore {
        forAll(Gen.listOf(transactionEntityGen())) { transactions =>
          exec(TransactionSchema.table.delete)
          exec(TransactionSchema.table ++= transactions)

          transactions foreach { transaction =>
            val query =
              sql"""
                   SELECT *
                   FROM #${TransactionSchema.name}
                   where hash = ${transaction.hash}
                   """

            val explain = exec(query.explain()).mkString("\n")

            explain should include("Index Scan on txs_pk")
          }
        }
      }
    }
  }

  "filterExistingAddresses & areAddressesActiveActionNonConcurrent" should {
    "return address that exist in DB" in {
      // generate two lists: Left to be persisted/existing & right as non-existing.
      forAll(
        Gen.listOf(genTransactionPerAddressEntity()),
        Gen.listOf(genTransactionPerAddressEntity())
      ) { case (existing, nonExisting) =>
        // clear the table
        exec(TransactionPerAddressSchema.table.delete)
        // persist existing entities
        exec(TransactionPerAddressSchema.table ++= existing)

        // join existing and nonExisting entities and shuffle
        val allEntities = Random.shuffle(existing ++ nonExisting)
        val allAddresses: List[ApiAddress] =
          allEntities.map { entity =>
            entity.address.lockupScript match {
              case LockupScript.P2PK(pubKey, _) =>
                if (Random.nextBoolean()) {
                  ApiAddress.fromProtocol(entity.address)
                } else {
                  // if P2PK, we can also return the half-decoded version
                  ApiAddress(ApiAddress.HalfDecodedP2PK(pubKey))
                }
              case _ =>
                ApiAddress.fromProtocol(entity.address)
            }
          }

        // get persisted entities to assert against actual query results
        val existingAddresses = existing.map(_.address)

        // fetch actual persisted addresses that exists
        val actualExisting =
          exec(TransactionQueries.filterExistingAddresses(allAddresses.toSet))

        // actual address should contain all of existingAddresses
        actualExisting should contain theSameElementsAs existingAddresses

        // exec boolean query
        val booleanExistingResult =
          exec(TransactionQueries.areAddressesActiveAction(allAddresses))

        // boolean query should output results in the same order as the input addresses
        (allAddresses.map(address =>
          actualExisting.exists(a => UtxoUtil.addressEqual(a, address))
        ): ArraySeq[Boolean]) is booleanExistingResult

        // check the boolean query with the existing concurrent query
        exec(
          TransactionQueries.areAddressesActiveAction(allAddresses)
        ) is booleanExistingResult
      }
    }
  }

  "getTxHashesByAddressQueryTimeRanged" should {
    "return empty" when {
      "database is empty" in {
        forAll(addressGen, timestampGen, timestampGen, Gen.posNum[Int], Gen.posNum[Int]) {
          (address, fromTime, toTime, page, limit) =>
            val query =
              TransactionQueries.getTxHashesByAddressQueryTimeRanged(
                address,
                fromTime,
                toTime,
                Pagination.unsafe(page, limit)
              )

            exec(query).size is 0
        }
      }
    }

    "return transactions_per_address within the range" when {
      "there is exactly one transaction per address (each address and timestamp belong to only one transaction)" in {
        forAll(Gen.listOf(genTransactionPerAddressEntity(mainChain = Gen.const(true)))) {
          entities =>
            // clear table and insert entities
            exec(TestQueries.clearAndInsert(entities))

            entities foreach { entity =>
              // exec query for each entity and expect that same entity to be returned
              val query =
                TransactionQueries
                  .getTxHashesByAddressQueryTimeRanged(
                    address = entity.address,
                    fromTime = entity.timestamp,
                    toTime = entity.timestamp,
                    pagination = Pagination.unsafe(1, Int.MaxValue)
                  )

              val actualResult = exec(query)

              val expectedResult =
                TxByAddressQR(
                  entity.hash,
                  entity.blockHash,
                  entity.timestamp,
                  entity.txOrder,
                  entity.coinbase
                )

              actualResult should contain only expectedResult
            }
        }
      }

      "there are multiple transactions per address" in {
        // a narrowed time range so there is overlap between transaction timestamps
        val timeStampGen =
          Gen.chooseNum(0L, 10L).map(TimeStamp.unsafe)

        val genTransactionsPerAddress =
          addressGen flatMap { address =>
            // for a single address generate multiple `TransactionPerAddressEntity`
            val entityGen =
              genTransactionPerAddressEntity(
                addressGen = Gen.const(address),
                timestampGen = timeStampGen
              )

            Gen.listOf(entityGen)
          }

        forAll(genTransactionsPerAddress) { entities =>
          // clear table and insert entities
          exec(TestQueries.clearAndInsert(entities))

          // for each address exec query for randomly selected time-range and expect entities
          // only for that time-range to be returned
          entities.groupBy(_.address) foreachEntry { case (address, entities) =>
            val minTime = entities.map(_.timestamp).min.millis // minimum time
            val maxTime = entities.map(_.timestamp).max.millis // maximum time

            // randomly select time range from the above minimum and maximum time range
            val timeRangeGen =
              for {
                fromTime <- Gen.chooseNum(minTime, maxTime)
                toTime   <- Gen.chooseNum(fromTime, maxTime)
              } yield (TimeStamp.unsafe(fromTime), TimeStamp.unsafe(toTime))

            // Run test on multiple randomly generated time-ranges on the same test data persisted
            forAll(timeRangeGen) { case (fromTime, toTime) =>
              // exec query for the generated time-range
              val query =
                TransactionQueries
                  .getTxHashesByAddressQueryTimeRanged(
                    address = address,
                    fromTime = fromTime,
                    toTime = toTime,
                    pagination = Pagination.unsafe(1, Int.MaxValue)
                  )

              val actualResult = exec(query)

              // expect only main_chain entities within the queried time-range
              val expectedEntities =
                entities filter { entity =>
                  entity.mainChain && entity.timestamp >= fromTime && entity.timestamp <= toTime
                }

              // transform query result TxByAddressQR
              val expectedResult =
                expectedEntities map { entity =>
                  TxByAddressQR(
                    entity.hash,
                    entity.blockHash,
                    entity.timestamp,
                    entity.txOrder,
                    entity.coinbase
                  )
                }

              // only the main_chain entities within the time-range is expected
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
        forAll(Gen.listOf(apiAddressGen), Gen.posNum[Int], Gen.posNum[Int]) {
          (addresses, page, limit) =>
            val query =
              TransactionQueries.getTxHashesByAddressesQuery(
                addresses,
                None,
                None,
                Pagination.unsafe(page, limit)
              )

            exec(query).size is 0
        }
      }
    }

    "return valid transactions_per_addresses" when {
      "the input contains valid and invalid addresses" in {
        forAll(Gen.listOf(genTransactionPerAddressEntity()), Gen.listOf(apiAddressGen)) {
          case (entitiesToPersist, addressesNotPersisted) =>
            // clear table and insert entities
            exec(TransactionPerAddressSchema.table.delete)
            exec(TransactionPerAddressSchema.table ++= entitiesToPersist)

            // merge all addresses
            val allAddresses =
              entitiesToPersist.map(entity =>
                ApiAddress.fromProtocol(entity.address)
              ) ++ addressesNotPersisted

            // shuffle all merged addresses
            val shuffledAddresses =
              Random.shuffle(allAddresses)

            // query shuffled addresses
            val query =
              TransactionQueries.getTxHashesByAddressesQuery(
                shuffledAddresses,
                None,
                None,
                Pagination.unsafe(1, Int.MaxValue)
              )

            // expect only main_chain and persisted address
            val expectedResult =
              entitiesToPersist.filter(_.mainChain) map { entity =>
                TxByAddressQR(
                  entity.hash,
                  entity.blockHash,
                  entity.timestamp,
                  entity.txOrder,
                  entity.coinbase
                )
              }

            exec(query) should contain theSameElementsAs expectedResult
        }
      }
    }

    "return timed range transactions" in new Fixture {
      forAll(
        Gen.nonEmptyListOf(
          genTransactionPerAddressEntity(
            addressGen = Gen.const(address),
            mainChain = Gen.const(true)
          )
        )
      ) { entities =>
        exec(TransactionPerAddressSchema.table.delete)
        exec(TransactionPerAddressSchema.table ++= entities)

        val timestamps = entities.map(_.timestamp).distinct
        val max        = timestamps.max
        val min        = timestamps.min

        def query(fromTs: Option[TimeStamp], toTs: Option[TimeStamp]) = {
          exec(
            TransactionQueries.getTxHashesByAddressesQuery(
              Seq(grouplessAddress),
              fromTs,
              toTs,
              Pagination.unsafe(1, Int.MaxValue)
            )
          )
        }

        def expected(entities: Seq[TransactionPerAddressEntity]) = {
          entities.map { entity =>
            TxByAddressQR(
              entity.hash,
              entity.blockHash,
              entity.timestamp,
              entity.txOrder,
              entity.coinbase
            )
          }
        }

        def test(
            fromTs: Option[TimeStamp],
            toTs: Option[TimeStamp],
            expectedEntites: Seq[TxByAddressQR]
        ) = {
          query(fromTs, toTs) should contain theSameElementsAs expectedEntites
        }

        // fromTs is inclusive
        test(fromTs = Some(max), toTs = None, expected(Seq(entities.maxBy(_.timestamp))))

        // toTs is exclusive
        test(fromTs = None, toTs = Some(max), expected(entities.sortBy(_.timestamp).init))

        // Verifying  max+1 include the last element
        test(
          fromTs = None,
          toTs = Some(max.plusMillisUnsafe(1)),
          expected(entities.sortBy(_.timestamp))
        )

        // excluding min and max elememt
        test(
          fromTs = Some(min.plusMillisUnsafe(1)),
          toTs = Some(max),
          expected(entities.sortBy(_.timestamp).init.drop(1))
        )

      }
    }
  }

  trait Fixture {

    val address          = addressGen.sample.get
    val grouplessAddress = ApiAddress.fromBase58(address.toBase58).rightValue
    def timestamp        = timestampGen.sample.get

    val chainFrom = GroupIndex.Zero
    val chainTo   = GroupIndex.Zero

    val lastFinalizedTime = TimeStamp.zero

    def output(address: Address, amount: U256, lockTime: Option[TimeStamp]): OutputEntity =
      outputEntityGen.sample.get.copy(
        timestamp = timestamp,
        hint = 0,
        key = hashGen.sample.get,
        amount = amount,
        address = address,
        grouplessAddress = AddressUtil.convertToGrouplessAddress(address),
        mainChain = true,
        lockTime = lockTime,
        txOrder = 0,
        coinbase = false
      )

    def input(output: OutputEntity): InputEntity =
      inputEntityGen().sample.get.copy(
        hint = output.hint,
        outputRefKey = output.key,
        mainChain = true,
        txOrder = 0
      )

    def transaction(output: OutputEntity): TransactionEntity = {
      transactionEntityGen().sample.get.copy(
        hash = output.txHash,
        blockHash = output.blockHash,
        timestamp = output.timestamp,
        mainChain = true,
        order = 0,
        chainFrom = GroupIndex.Zero,
        chainTo = new GroupIndex(1),
        version = 1,
        networkId = 1,
        scriptOpt = None,
        gasAmount = 1,
        gasPrice = ALPH.alph(1),
        scriptExecutionOk = true,
        coinbase = false
      )
    }

    def transaction(
        transactionEntity: TransactionEntity,
        output: OutputEntity,
        spent: Option[TransactionId],
        inputs: ArraySeq[Input]
    ) = {
      transactionEntity.toApi(
        inputs = inputs,
        outputs = ArraySeq(outputEntityToApi(output, spent))
      )
    }
  }
}
