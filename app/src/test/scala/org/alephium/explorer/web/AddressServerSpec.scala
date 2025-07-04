// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import akka.util.ByteString
import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen
import org.scalatest.time.{Seconds, Span}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.{Header, StatusCode}

import org.alephium.api.ApiError
import org.alephium.api.model.{Address => ApiAddress, TimeInterval}
import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.config.Default.groupConfig
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.service.{
  EmptyTokenService,
  EmptyTransactionService,
  TransactionService
}
import org.alephium.protocol.PublicKey
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.protocol.vm.{LockupScript, UnlockScript}
import org.alephium.serde._
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.PlatformDefault",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class AddressServerSpec()
    extends AlephiumActorSpecLike
    with DatabaseFixtureForAll
    with HttpServerFixture {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(120, Seconds))

  val exportTxsNumberThreshold = 1000
  var addressHasMoreTxs        = false

  val transactions: ArraySeq[Transaction] =
    ArraySeq.from(Gen.listOfN(30, transactionGen).sample.get.zipWithIndex.map { case (tx, index) =>
      tx.copy(timestamp = TimeStamp.now() + Duration.ofDaysUnsafe(index.toLong))
    })
  val mempoolTx = mempooltransactionGen.sample.get
//producing some random data, it isn't a real history
  val amountHistory = transactions
    .flatMap { tx =>
      tx.outputs.map { output =>
        (output.attoAlphAmount.v, tx.timestamp)
      }
    }
    .sortBy(_._2)

  val tokens = Gen.listOf(addressTokenBalanceGen).sample.get

  val transactionInfo = transactions.headOption.map { tx =>
    TransactionInfo(
      tx.hash,
      tx.blockHash,
      tx.timestamp,
      tx.coinbase
    )
  }

  var testLimit = 0

  val publicKeyAddresses = Gen.listOfN(10, publicKeyGen).sample.get.map { publicKey =>
    (Address.p2pkh(publicKey), publicKey)
  }

  val transactionService = new EmptyTransactionService {
    override def listMempoolTransactionsByAddress(address: ApiAddress)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[MempoolTransaction]] = {
      Future.successful(ArraySeq(mempoolTx))
    }

    override def getTransactionsByAddress(
        address: ApiAddress,
        pagination: Pagination
    )(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[Transaction]] = {
      testLimit = pagination.limit
      Future.successful(ArraySeq.empty)
    }

    override def hasAddressMoreTxsThan(
        address: ApiAddress,
        from: TimeStamp,
        to: TimeStamp,
        threshold: Int
    )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Boolean] =
      Future.successful(addressHasMoreTxs)

    override def exportTransactionsByAddress(
        address: ApiAddress,
        from: TimeStamp,
        to: TimeStamp,
        batchSize: Int,
        streamParallelism: Int
    )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Flowable[Buffer] = {
      TransactionService.transactionsFlowable(
        address,
        Flowable
          .fromIterable(transactions.asJava)
          .buffer(batchSize)
          .map(l => (ArraySeq.from(l.asScala), Map.empty))
      )
    }

    override def getLatestTransactionInfoByAddress(address: ApiAddress)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[Option[TransactionInfo]] =
      Future.successful(transactionInfo)

    override def getAmountHistory(
        address: ApiAddress,
        from: TimeStamp,
        to: TimeStamp,
        intervalType: IntervalType
    )(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[(TimeStamp, BigInteger)]] =
      Future.successful(amountHistory.map { case (ts, bi) => (bi, ts) })

    override def getUnlockScript(
        address: ApiAddress
    )(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[Option[ByteString]] = {
      Future.successful {
        publicKeyAddresses.find(_._1.toBase58 == address.toBase58).map { case (_, publicKey) =>
          val unlockScript: UnlockScript = UnlockScript.P2PKH(publicKey)
          serialize(unlockScript)
        }
      }
    }
  }

  val tokenService = new EmptyTokenService {
    override def listAddressTokensWithBalance(address: ApiAddress, pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[(TokenId, U256, U256)]] =
      Future.successful {
        tokens.map(res => (res.tokenId, res.balance, res.lockedBalance))
      }
  }

  implicit val blockCache: BlockCache = TestBlockCache()

  val server =
    new AddressServer(
      transactionService,
      tokenService,
      exportTxsNumberThreshold = 1000,
      streamParallelism = 8,
      maxTimeInterval = ConfigDefaults.maxTimeIntervals.amountHistory,
      maxTimeIntervalExportTxs = ConfigDefaults.maxTimeIntervals.exportTxs
    )

  val routes = server.routes

  "validate and forward `txLimit` query param" in {
    val maxLimit = Pagination.maxLimit
    forAll(addressGen, Gen.chooseNum[Int](-10, 120)) { case (address, txLimit) =>
      Get(s"/addresses/${address}/transactions?limit=$txLimit") check { response =>
        if (txLimit < 0) {
          response.code is StatusCode.BadRequest
          response.as[ApiError.BadRequest] is ApiError.BadRequest(
            s"Invalid value for: query parameter limit (expected value to be greater than or equal to 0, but got $txLimit)"
          )
        } else if (txLimit > maxLimit) {
          response.code is StatusCode.BadRequest
          response.as[ApiError.BadRequest] is ApiError.BadRequest(
            s"Invalid value for: query parameter limit (expected value to be less than or equal to $maxLimit, but got $txLimit)"
          )
        } else {
          response.code is StatusCode.Ok
          testLimit is txLimit
        }
      }

      Get(s"/addresses/${address}/transactions") check { _ =>
        testLimit is Pagination.defaultLimit // default txLimit
      }
    }
  }

  "get total transactions" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}/total-transactions") check { response =>
        response.as[Int] is 0
      }
    }
  }

  "get latest transaction info" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}/latest-transaction") check { response =>
        response.as[TransactionInfo] is transactionInfo.get
      }
    }
  }

  "get balance" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}/balance") check { response =>
        response.as[AddressBalance] is AddressBalance(U256.Zero, U256.Zero)
      }
    }
  }

  "get address info" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}") check { response =>
        response.as[AddressInfo] is AddressInfo(U256.Zero, U256.Zero, 0)
      }
    }
  }

  "check if addresses are active" in {
    forAll(addressGen) { case address =>
      val entity = s"""["$address"]"""
      Post(s"/addresses/used", Some(entity)) check { response =>
        response.as[ArraySeq[Boolean]] is ArraySeq(true)
      }
    }
  }

  "respect the max number of addresses" in {
    forAll(addressGen)(respectMaxNumberOfAddresses("/addresses/used", _))
  }

  "list mempool transactions for a given address" in {
    forAll(addressGen) { case address =>
      Get(s"/addresses/${address}/mempool/transactions") check { response =>
        response.as[ArraySeq[MempoolTransaction]] is ArraySeq(mempoolTx)
      }
    }
  }

  "/addresses/<address>/export-transactions/" should {
    "handle csv format" in {
      val address    = addressGen.sample.get
      val timestamps = transactions.map(_.timestamp.millis).sorted
      val fromTs     = timestamps.head
      val toTs       = timestamps.last

      Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toTs") check {
        response =>
          response.body is Right(
            Transaction.csvHeader ++ transactions.map(_.toCsv(address, Map.empty)).mkString
          )

          val header =
            Header("Content-Disposition", s"""attachment;filename="$address-$fromTs-$toTs.csv"""")
          response.headers.contains(header) is true
      }
    }
    "restrict time range to 1 year" in {
      val address = addressGen.sample.get
      val long    = Gen.posNum[Long].sample.get
      val fromTs  = TimeStamp.now().millis
      val toTs    = fromTs + Duration.ofDaysUnsafe(366).millis

      Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toTs") check {
        response =>
          response.code is StatusCode.Ok
      }

      val toMore = toTs + Duration.ofMillisUnsafe(long).millis
      Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toMore") check {
        response =>
          response.code is StatusCode.BadRequest
      }
    }
  }

  "fail if address has more txs than the threshold" in {
    addressHasMoreTxs = true
    val address = addressGen.sample.get
    Get(s"/addresses/${address}/export-transactions/csv?fromTs=0&toTs=1") check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        s"Too many transactions for that address in this time range, limit is $exportTxsNumberThreshold"
      )
    }
  }

  "/addresses/<address>/amount-history" should {
    val address       = addressGen.sample.get
    val timestamps    = transactions.map(_.timestamp.millis).sorted
    val intervalTypes = ArraySeq[IntervalType](IntervalType.Hourly, IntervalType.Daily)
    val fromTs        = timestamps.head
    def maxTimeSpan(intervalType: IntervalType) = intervalType match {
      case IntervalType.Hourly => Duration.ofDaysUnsafe(7)
      case IntervalType.Daily  => Duration.ofDaysUnsafe(366)
      case IntervalType.Weekly => Duration.ofDaysUnsafe(366)
    }
    def getToTs(intervalType: IntervalType) =
      fromTs + maxTimeSpan(intervalType).millis

    "return the amount history as json" in {
      intervalTypes.foreach { intervalType =>
        val toTs = getToTs(intervalType)

        Get(
          s"/addresses/${address}/amount-history?fromTs=$fromTs&toTs=$toTs&interval-type=$intervalType"
        ) check { response =>
          response.body is Right(
            s"""{"amountHistory":${amountHistory
                .map { case (amount, ts) => s"""[${ts.millis},"$amount"]""" }
                .mkString("[", ",", "]")}}"""
          )
        }
      }
    }

    "respect the time range and time interval" in {
      intervalTypes.foreach { intervalType =>
        val wrongToTs = getToTs(intervalType) + 1

        Get(
          s"/addresses/${address}/amount-history?fromTs=$fromTs&toTs=$wrongToTs&interval-type=$intervalType"
        ) check { response =>
          response.body is Left(
            s"""{"detail":"Time span cannot be greater than ${maxTimeSpan(intervalType)}"}"""
          )
        }
      }
    }
  }

  "getTransactionsByAddresses" should {
    "list transactions for an array of addresses" in {
      forAll(addressGen) { address =>
        Post("/addresses/transactions", s"""["$address"]""") check { response =>
          response.as[ArraySeq[Transaction]] is ArraySeq.empty[Transaction]
        }
      }
    }

    "respect the max number of addresses" in {
      forAll(addressGen)(respectMaxNumberOfAddresses("/addresses/transactions", _))
    }
  }

  "listAddressTokenTransactions" should {
    "list tokens with their balance for a given address" in {
      forAll(addressGen) { address =>
        Get(s"/addresses/$address/tokens-balance") check { response =>
          response.as[ArraySeq[AddressTokenBalance]] is tokens
        }
      }
    }
  }

  "getPublicKey" should {
    "not found when inputs don't exist" in {
      forAll(addressGen) { address =>
        Get(s"/addresses/$address/public-key") check { response =>
          address match {
            case Address.Asset(LockupScript.P2PKH(_))   => response.code is StatusCode.NotFound
            case Address.Asset(LockupScript.P2PK(_, _)) => response.code is StatusCode.Ok
            case _                                      => response.code is StatusCode.BadRequest
          }
        }
      }
    }

    "return public key" in {
      publicKeyAddresses.foreach { case (address, publicKey) =>
        Get(s"/addresses/$address/public-key") check { response =>
          response.as[PublicKey] is publicKey
        }
      }
    }
  }

  "AddressServer companion object" should {
    "create correct export filename" in {
      val address = "12jK2jHyyJTJyuRMRya7QJSojgVnb5yh4HVzNNw6BTBDF"
      val from    = 1234L
      val to      = 5678L

      val expected = s"""attachment;filename="$address-$from-$to.csv""""
      AddressServer.exportFileNameHeader(
        ApiAddress.fromBase58(address).rightValue,
        TimeInterval(TimeStamp.unsafe(from), TimeStamp.unsafe(to))
      ) is expected
    }

    "convert p2pkh unlock script" in {
      forAll(unlockScriptProtocolP2PKHGen) { unlockScript =>
        val publicKey = unlockScript.publicKey
        AddressServer.bytesToPublicKey(serialize(unlockScript.asInstanceOf[UnlockScript])) is Right(
          publicKey
        )
      }
    }

    "fail to convert p2mpkh unlock script" in {
      forAll(unlockScriptProtocolP2MPKHGen) { unlockScript =>
        AddressServer.bytesToPublicKey(serialize(unlockScript.asInstanceOf[UnlockScript])) is Left(
          ApiError.BadRequest("Invalid unlock script, require P2PKH")
        )
      }
    }

    "fail to convert invalid unlock script" in {
      forAll(hashGen) { unlockScript =>
        val error = deserialize[UnlockScript](unlockScript.bytes).swap.toOption.get
        AddressServer.bytesToPublicKey(unlockScript.bytes) is Left(
          ApiError.InternalServerError(s"Failed to deserialize unlock script: $error")
        )
      }
    }
  }

  def respectMaxNumberOfAddresses(endpoint: String, address: Address) = {
    val size = groupSetting.groupNum * 20

    val jsonOk = s"[${ArraySeq.fill(size)(s""""$address"""").mkString(",")}]"
    Post(endpoint, Some(jsonOk)) check { response =>
      response.code is StatusCode.Ok
    }

    val jsonFail = s"[${ArraySeq.fill(size + 1)(s""""$address"""").mkString(",")}]"
    Post(endpoint, Some(jsonFail)) check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        s"Invalid value for: body (expected size of value to be less than or equal to $size, but got ${size + 1})"
      )
    }
  }
}
