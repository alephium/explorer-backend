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

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.{TimeStamp, U256}

trait TransactionQueries
    extends TransactionSchema
    with InputSchema
    with OutputSchema
    with TransactionPerAddressSchema
    with StrictLogging {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private val mainTransactions = transactionsTable.filter(_.mainChain)
  private val mainInputs       = inputsTable.filter(_.mainChain)
  private val mainOutputs      = outputsTable.filter(_.mainChain)

  def insertTransactionFromBlockQuery(blockEntity: BlockEntity): DBActionW[Unit] = {
    for {
      _ <- DBIOAction.sequence(blockEntity.transactions.map(transactionsTable.insertOrUpdate))
      _ <- DBIOAction.sequence(blockEntity.outputs.map(outputsTable.insertOrUpdate))
      _ <- DBIOAction.sequence(blockEntity.inputs.map(inputsTable.insertOrUpdate))
      _ <- DBIOAction.sequence(blockEntity.inputs.map(updateSpentOutput))
      _ <- DBIOAction.sequence(blockEntity.inputs.map(updateInputAddress))
      _ <- insertTxPerAddressFromOutputs(blockEntity.outputs)
      _ <- DBIOAction.sequence(blockEntity.inputs.map(updateTxPerAddressFromInputs))
    } yield ()
  }

  def updateSpentOutput(input:InputEntity):DBActionW[Int] = {
    val query = s"""
    UPDATE outputs
    SET spent = '\\x${input.txHash}'
    WHERE key = '\\x${input.outputRefKey.toHexString}'
    """
    sqlu"#$query"

  }

  def updateInputAddress(input:InputEntity):DBActionW[Int] = {
     val query = s"""
    UPDATE inputs
    SET address = out.address,
        output_tx_hash = out.tx_hash,
        output_amount = out.amount
    FROM (SELECT outputs.address, outputs.tx_hash, outputs.amount FROM outputs where key = '\\x${input.outputRefKey.toHexString}' LIMIT 1) AS out
    WHERE inputs.output_ref_key = '\\x${input.outputRefKey.toHexString}' AND inputs.tx_hash = '\\x${input.txHash}' AND inputs.block_hash = '\\x${input.blockHash}'
    """
     sqlu"#$query"
  }

  def insertTxPerAddressFromOutputs(outputs:Seq[OutputEntity]):DBActionW[Int] = {
    if(outputs.nonEmpty) {
    val values = outputs.map { output =>
    val instant = java.time.Instant.ofEpochMilli(output.timestamp.millis)
      s"('\\x${output.txHash}','\\x${output.blockHash}','${instant}','${output.address}',${output.mainChain})"
    }.mkString(",\n")
    val query =s"""
      INSERT INTO transaction_per_addresses (hash, block_hash, timestamp, address, main_chain)
      VALUES $values
      ON CONFLICT (hash, block_hash, address) DO NOTHING
    """
    sqlu"#$query"

    } else {
      DBIOAction.successful(0)
    }
  }

  def updateTxPerAddressFromInputs(input:InputEntity):DBActionW[Int] = {
   val query = s"""
      INSERT INTO transaction_per_addresses (hash, block_hash, timestamp, address, main_chain)
      (SELECT inputs.tx_hash, inputs.block_hash, inputs.timestamp, inputs.address, inputs.main_chain FROM inputs WHERE inputs.address IS NOT NULL AND inputs.output_ref_key = '\\x${input.outputRefKey.toHexString}' AND inputs.tx_hash = '\\x${input.txHash}' AND inputs.block_hash = '\\x${input.blockHash}')
      ON CONFLICT (hash, block_hash, address) DO NOTHING
    """

    sqlu"#$query"
  }

  def insertAllTransactionFromBlockQuerys(blockEntities: Seq[BlockEntity]): DBActionW[Unit] = {
    DBIOAction.sequence(blockEntities.map(insertTransactionFromBlockQuery)).map(_ => ())
  }

  private val countBlockHashTransactionsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    transactionsTable.filter(_.blockHash === blockHash).length
  }

  def countBlockHashTransactions(blockHash: BlockEntry.Hash): DBActionR[Int] =
    countBlockHashTransactionsQuery(blockHash).result

  def countAddressTransactions(address: Address): DBActionR[Int] =
    getTxNumberByAddressQuery(address).result

  def countAddressTransactionsSQL(address: Address): DBActionSR[Int] ={
     val query = s"""
    SELECT COUNT(*)
    FROM (
    SELECT tx_hash from outputs WHERE address = '$address'
    UNION
    SELECT tx_hash from inputs WHERE address = '$address'
    ) tx_hashes
    """
     sql"""#$query""".as[Int]
  }

  def countAddressTransactionsSQLNoJoin(address: Address): DBActionSR[Int] ={
     val query = s"""
    SELECT COUNT(*) FROM transaction_per_addresses WHERE main_chain = true AND address = '$address'
    """
     sql"""#$query""".as[Int]
  }

  def getTxHashesByAddressQuerySQL(address: Address, offset:Int, limit: Int): DBActionSR[(Transaction.Hash, BlockEntry.Hash, TimeStamp)] ={
     val query = s"""
    SELECT tx_hash, block_hash, timestamp from outputs WHERE address = '$address'
    UNION
    SELECT tx_hash, block_hash, timestamp from inputs WHERE address = '$address'
    LIMIT $limit
    OFFSET $offset
    """
     sql"""#$query""".as[(Transaction.Hash, BlockEntry.Hash, TimeStamp)]
  }

  def getTxHashesByAddressQuerySQLNoJoin(address: Address, offset:Int, limit: Int): DBActionSR[(Transaction.Hash, BlockEntry.Hash, TimeStamp)] ={
     val query = s"""
    SELECT hash, block_hash, timestamp from transaction_per_addresses WHERE main_chain = true AND address = '$address'
    LIMIT $limit
    OFFSET $offset
    """
     sql"""#$query""".as[(Transaction.Hash, BlockEntry.Hash, TimeStamp)]
  }


  private val getTransactionQuery = Compiled { txHash: Rep[Transaction.Hash] =>
    mainTransactions
      .filter(_.hash === txHash)
      .map(tx => (tx.blockHash, tx.timestamp, tx.gasAmount, tx.gasPrice))
  }

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).result.headOption.flatMap {
      case None => DBIOAction.successful(None)
      case Some((blockHash, timestamp, gasAmount, gasPrice)) =>
        getKnownTransactionAction(txHash, blockHash, timestamp, gasAmount, gasPrice).map(Some.apply)
    }

  private val getTxHashesByBlockHashQuery = Compiled { (blockHash: Rep[BlockEntry.Hash]) =>
    transactionsTable
      .filter(_.blockHash === blockHash)
      .sortBy(_.txIndex)
      .map(tx => (tx.hash, tx.blockHash, tx.timestamp))
  }

  private val getTxHashesByBlockHashWithPaginationQuery = Compiled {
    (blockHash: Rep[BlockEntry.Hash], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      transactionsTable
        .filter(_.blockHash === blockHash)
        .sortBy(_.txIndex)
        .map(tx => (tx.hash, tx.blockHash, tx.timestamp))
        .drop(toDrop)
        .take(limit)
  }

  private val getTxNumberByAddressQuery = Compiled { (address: Rep[Address]) =>
    mainInputs
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .filter(_._2.address === address)
      .map { case (input, _) => input.txHash }
      .union(
        mainOutputs
          .filter(_.address === address)
          .map(out => out.txHash)
      )
      .length
  }

  val getTxHashesByAddressQuery = Compiled {
    (address: Rep[Address], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      mainInputs
        .join(mainOutputs)
        .on(_.outputRefKey === _.key)
        .filter(_._2.address === address)
        .map { case (input, _) => input.txHash }
        .join(mainTransactions)
        .on(_ === _.hash)
        .union(
          mainOutputs
            .filter(_.address === address)
            .map(out => out.txHash)
            .join(mainTransactions)
            .on(_ === _.hash)
        )
        .sortBy { case (_, tx) => (tx.timestamp.desc, tx.txIndex) }
        .map { case (_, tx) => (tx.hash, tx.blockHash, tx.timestamp) }
        .drop(toDrop)
        .take(limit)
  }

  def inputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainInputs
      .filter(_.txHash inSet txHashes)
      .join(mainOutputs)
      .on {
        case (input, outputs) =>
          input.outputRefKey === outputs.key
      }
      .map {
        case (input, output) =>
          (input.txHash,
           (input.hint,
            input.outputRefKey,
            input.unlockScript,
            output.txHash,
            output.address,
            output.amount),
           input.order)
      }
  }

  def inputsFromTxsSQL(txHashes: Seq[Transaction.Hash]) = {
    val values = txHashes.map(hash => s"inputs.tx_hash = '\\x$hash'").mkString(" OR ")
    val state = sql"""
    SELECT (inputs.tx_hash, inputs.hint, inputs.output_ref_key, inputs.unlock_script, outputs.tx_hash, outputs.address, outputs.amount, inputs.order) FROM inputs
    JOIN outputs
    ON inputs.output_ref_key = outputs.key
    """.as[(Transaction.Hash,Int, Hash, Option[String], Transaction.Hash, Address, U256, Int)]
    println(s"${Console.RED}${Console.BOLD}*** state ***\n\t${Console.RESET}${state.statements}")
    state
  }

  def inputsFromTxsSQLNoJoin(txHashes: Seq[Transaction.Hash]) = {
    val query = s"""
    SELECT (tx_hash, hint, output_ref_key, unlock_script, output_tx_hash, address, output_amount, order) FROM inputs
    WHERE inputs.tx_hash in (${txHashes.map(hash => s"'\\x$hash'").mkString(",")})
    """
    sql"#$query".as[(Transaction.Hash,Int, Hash, Option[String], Transaction.Hash, Address, U256, Int)]
  }

  def inputsFromTxsNoJoin(txHashes: Seq[Transaction.Hash]) = {
    mainInputs
      .filter(_.txHash inSet txHashes)
      .map {input =>
          (input.txHash,
           (input.hint,
            input.outputRefKey,
            input.unlockScript,
            input.outputTxHash,
            input.address,
            input.outputAmount),
           input.order)
      }
  }

  private def outputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainOutputs
      .filter(_.txHash inSet txHashes)
      .joinLeft(mainInputs)
      .on {
        case (out, inputs) =>
          out.key === inputs.outputRefKey
      }
      .map {
        case (output, input) =>
          (output.txHash,
           (output.hint,
            output.key,
            output.amount,
            output.address,
            output.lockTime,
            input.map(_.txHash)),
           output.order)
      }
  }

  private def outputsFromTxsSQL(txHashes: Seq[Transaction.Hash]) = {
    mainOutputs
      .filter(_.txHash inSet txHashes)
      .map {output=>
          (output.txHash,
           (output.hint,
            output.key,
            output.amount,
            output.address,
            output.lockTime,
            output.spent),
           output.order)
      }
  }

  def getTransactionsByBlockHash(blockHash: BlockEntry.Hash): DBActionR[Seq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByBlockHashQuery(blockHash).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByBlockHashWithPagination(
      blockHash: BlockEntry.Hash,
      pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByBlockHashWithPaginationQuery((blockHash, toDrop, limit)).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddress(address: Address,
                               pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByAddressQuery((address, toDrop, limit)).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddressSQL(address: Address,
                               pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByAddressQuerySQL(address, toDrop, limit)
      txs        <- getTransactionsSQL(txHashesTs)
    } yield txs
  }


  def getTransactions(txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp)])
    : DBActionR[Seq[Transaction]] = {
    val txHashes = txHashesTs.map(_._1)
    for {
      ins <- inputsFromTxs(txHashes).result
      ous <- outputsFromTxs(txHashes).result
      gas <- gasFromTxs(txHashes).result
    } yield {
      val insByTx = ins.groupBy(_._1).view.mapValues { values =>
        values
          .sortBy {
            case (_, _, index) => index
          }
          .map {
            case (_, input, _) =>
              toApiInput(input)
          }
      }
      val ousByTx = ous.groupBy(_._1).view.mapValues { values =>
        values
          .sortBy {
            case (_, _, index) => index
          }
          .map {
            case (_, out, _) =>
              toApiOutput(out)
          }
      }
      val gasByTx = gas.groupBy(_._1).view.mapValues(_.map { case (_, s, g) => (s, g) })
      txHashesTs.map {
        case (tx, bh, ts) =>
          val ins                   = insByTx.getOrElse(tx, Seq.empty)
          val ous                   = ousByTx.getOrElse(tx, Seq.empty)
          val gas                   = gasByTx.getOrElse(tx, Seq.empty)
          val (gasAmount, gasPrice) = gas.headOption.getOrElse((0, U256.Zero))
          Transaction(tx, bh, ts, ins, ous, gasAmount, gasPrice)
      }
    }
  }

  def getTransactionsSQL(txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp)])
    : DBActionR[Seq[Transaction]] = {
    val txHashes = txHashesTs.map(_._1)
    for {
      ins <- inputsFromTxsNoJoin(txHashes).result
      ous <- outputsFromTxsSQL(txHashes).result
      gas <- gasFromTxs(txHashes).result
    } yield {
      val insByTx = ins.groupBy(_._1).view.mapValues { values =>
        values
          .sortBy {
            case (_, _, index) => index
          }
          .map {
            case (_, input, _) =>
              toApiInputSQL(input)
          }
      }
      val ousByTx = ous.groupBy(_._1).view.mapValues { values =>
        values
          .sortBy {
            case (_, _, index) => index
          }
          .map {
            case (_, out, _) =>
              toApiOutput(out)
          }
      }
      val gasByTx = gas.groupBy(_._1).view.mapValues(_.map { case (_, s, g) => (s, g) })
      txHashesTs.map {
        case (tx, bh, ts) =>
          val ins                   = insByTx.getOrElse(tx, Seq.empty)
          val ous                   = ousByTx.getOrElse(tx, Seq.empty)
          val gas                   = gasByTx.getOrElse(tx, Seq.empty)
          val (gasAmount, gasPrice) = gas.headOption.getOrElse((0, U256.Zero))
          Transaction(tx, bh, ts, ins, ous, gasAmount, gasPrice)
      }
    }
  }

  private def gasFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainTransactions.filter(_.hash inSet txHashes).map(tx => (tx.hash, tx.gasAmount, tx.gasPrice))
  }

  private val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    mainInputs
      .filter(_.txHash === txHash)
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .sortBy(_._1.order)
      .map {
        case (input, output) =>
          (input.hint,
           input.outputRefKey,
           input.unlockScript,
           output.txHash,
           output.address,
           output.amount)
      }
  }

  private val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    outputsTable
      .filter(output => output.mainChain && output.txHash === txHash)
      .joinLeft(mainInputs)
      .on(_.key === _.outputRefKey)
      .sortBy(_._1.order)
      .map {
        case (output, input) =>
          (output.hint,
           output.key,
           output.amount,
           output.address,
           output.lockTime,
           input.map(_.txHash))
      }
  }

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        blockHash: BlockEntry.Hash,
                                        timestamp: TimeStamp,
                                        gasAmount: Int,
                                        gasPrice: U256): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash).result
      outs <- getOutputsQuery(txHash).result
    } yield {
      Transaction(txHash,
                  blockHash,
                  timestamp,
                  ins.map(toApiInput),
                  outs.map(toApiOutput),
                  gasAmount,
                  gasPrice)
    }

  def getUnlockedBalanceSQL(address: Address):DBActionSR[(U256,Option[TimeStamp])] = {
    val query = s"""
        SELECT outputs.amount, outputs.lock_time
        FROM outputs
        LEFT JOIN inputs ON outputs.key = inputs.output_ref_key
        WHERE outputs.main_chain = true AND outputs.address = '$address' AND inputs.block_hash IS NULL
      """
    sql"#$query".as[(U256,Option[TimeStamp])]
  }

  def getUnlockedBalanceSQLNoJoin(address: Address):DBActionSR[(U256,Option[TimeStamp])]  = {
    val query = s"""
        SELECT outputs.amount, outputs.lock_time
        FROM outputs
        WHERE outputs.main_chain = true AND outputs.address = '$address' AND spent IS NULL
      """
    sql"#$query".as[(U256,Option[TimeStamp])]
  }


  val getBalanceQuery = Compiled { address: Rep[Address] =>
    mainOutputs
      .filter(output => output.address === address)
      .joinLeft(mainInputs)
      .on(_.key === _.outputRefKey)
      .filter(_._2.isEmpty)
      .map { case (output, _) => (output.amount, output.lockTime) }
  }

     def getBalanceAction(address: Address): DBActionR[(U256, U256)] = {
    getBalanceQuery(address).result.map { outputs =>
      sumBalance(outputs)
    }}

     private def sumBalance(outputs: Seq[(U256,Option[TimeStamp])]) : (U256, U256) = {
      val now = TimeStamp.now()
      outputs.foldLeft((U256.Zero, U256.Zero)) {
        case ((total, locked), (amount, lockTime)) =>
          val newTotal = total.addUnsafe(amount)
          val newLocked = if (lockTime.map(_.isBefore(now)).getOrElse(true)) {
            locked
          } else {
            locked.addUnsafe(amount)
          }
          (newTotal, newLocked)
      }
     }

  def getBalanceActionSQL(address: Address): DBActionR[(U256, U256)] = {
      getUnlockedBalanceSQL(address).map(sumBalance)
  }

  def getBalanceActionSQLNoJoin(address: Address): DBActionR[(U256, U256)] = {
      getUnlockedBalanceSQLNoJoin(address).map(sumBalance)
  }

  private val toApiInput = {
    (hint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHash: Transaction.Hash,
     address: Address,
     amount: U256) =>
      Input(Output.Ref(hint, key), unlockScript, txHash, address, amount)
  }.tupled

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private val toApiInputSQL = {
    (hint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHash: Option[Transaction.Hash],
     address: Option[Address],
     amount: Option[U256]) =>
      Input(Output.Ref(hint, key), unlockScript, txHash.get, address.get, amount.get)
  }.tupled

  private val toApiOutput = (Output.apply _).tupled

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
