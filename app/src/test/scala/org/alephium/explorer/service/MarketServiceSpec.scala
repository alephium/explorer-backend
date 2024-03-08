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

package org.alephium.explorer.service

import java.net.InetAddress

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.Future

import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.ext.web._
import org.scalatest.concurrent.ScalaFutures
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.web.Server
import org.alephium.json.Json._

class MarketServiceSpec extends AlephiumFutureSpec {
  import MarketServiceSpec._

  "get prices, exchange rates and charts" in new Fixture {

    eventually {
      val prices =
        marketService.getPrices(marketConfig.symbolName.keys.toList, "btc").futureValue.rightValue

      prices.length is marketConfig.symbolName.length

      prices(marketConfig.symbolName.keys.indexOf(alph)) is Some(alphPrice)
      prices(marketConfig.symbolName.keys.indexOf(usdt)) is Some(usdtPrice)
    }

    eventually {
      marketConfig.currencies.foreach { currency =>
        val prices =
          marketService.getPrices(ArraySeq(alph, usdt), currency).futureValue.rightValue

        val exchangeRate =
          marketService.getExchangeRates().futureValue.rightValue.find(_.currency == currency).get

        prices(0) is Some(alphPrice * exchangeRate.value)
        prices(1) is Some(usdtPrice * exchangeRate.value)
      }
    }

    eventually {
      val prices = marketService.getPrices(ArraySeq("ALPH", "EMPTY"), "chf").futureValue.rightValue

      prices.length is 2
      prices(1) is None
    }

    eventually {
      val exchangeRates = marketService.getExchangeRates().futureValue.rightValue
      exchangeRates.map(_.currency).toSet is marketConfig.currencies.toSet
    }

    eventually {
      val btcChart = marketService.getPriceChart(alph, "btc").futureValue.rightValue

      marketConfig.currencies.foreach { currency =>
        val chart =
          marketService.getPriceChart(alph, currency).futureValue.rightValue
        val exchangeRate =
          marketService.getExchangeRates().futureValue.rightValue.find(_.currency == currency).get

        chart.timestamps.length is chart.prices.length
        chart.timestamps is btcChart.timestamps
        chart.prices is btcChart.prices.map { value => value * exchangeRate.value }
      }
    }
  }

  trait Fixture {
    val localhost: InetAddress = InetAddress.getByName("127.0.0.1")
    val port                   = SocketUtil.temporaryLocalPort(SocketUtil.Both)

    val alph = "ALPH"
    val usdt = "USDT"

    val marketConfig = ExplorerConfig.Market(
      ListMap(
        "ALPH" -> "alephium",
        "USDC" -> "usd-coin",
        "USDT" -> "tether",
        "WBTC" -> "wrapped-bitcoin",
        "WETH" -> "weth",
        "DAI"  -> "dai",
        "AYIN" -> "ayin"
      ),
      ArraySeq("btc", "usd", "eur", "chf", "gbp", "idr", "vnd", "rub", "try", "cad", "aud"),
      s"http://${localhost.getHostAddress()}:$port",
      marketChartDays = 366
    )

    val coingecko: MarketServiceSpec.CoingeckoMock =
      new MarketServiceSpec.CoingeckoMock(localhost, port)
    val marketService: MarketService =
      new MarketService.CoinGecko(marketConfig)
  }
}

object MarketServiceSpec {

  implicit val jsonSchema: Schema[ujson.Value] = Schema.string
  implicit val jsoncodec: Codec[String, ujson.Value, TextPlain] =
    Codec.string.map(value => ujson.read(value))(_.toString)

  val alphPrice = 2.013e-5
  val usdtPrice = 2.392e-5

  class CoingeckoMock(
      uri: InetAddress,
      port: Int
  ) extends ScalaFutures
      with BaseEndpoint
      with Server {

    private val vertx  = Vertx.vertx()
    private val router = Router.router(vertx)

    val routes: ArraySeq[Router => Route] =
      ArraySeq(
        route(
          baseEndpoint.get
            .in("simple")
            .in("price")
            .in(query[List[String]]("ids"))
            .in(query[String]("vs_currencies"))
            .out(jsonBody[ujson.Value])
            .serverLogicSuccess[Future] { case (_, _) =>
              Future.successful(ujson.read(prices))
            }
        ),
        route(
          baseEndpoint.get
            .in("exchange_rates")
            .out(jsonBody[ujson.Value])
            .serverLogicSuccess[Future] { _ =>
              Future.successful(ujson.read(exchangeRates))
            }
        ),
        route(
          baseEndpoint.get
            .in("coins")
            .in(path[String]("coin"))
            .in("market_chart")
            .in(query[String]("vs_currency"))
            .in(query[Int]("days"))
            .out(jsonBody[ujson.Value])
            .serverLogicSuccess[Future] { _ =>
              Future.successful(ujson.read(priceChart))
            }
        )
      )

    val server = vertx.createHttpServer().requestHandler(router)

    routes.foreach(route => route(router))

    server.listen(port, uri.getHostAddress).asScala.futureValue
  }

  val prices: String = s"""
    {"alephium":{"btc":$alphPrice},"ayin":{"btc":9.572E-5},"dai":{"btc":2.389E-5},"tether":{"btc":$usdtPrice},"usd-coin":{"btc":2.392E-5},"weth":{"btc":0.05312674},"wrapped-bitcoin":{"btc":1.000324}}
  """

  val exchangeRates: String = """
                {"rates":{"btc":{"name":"Bitcoin","unit":"BTC","value":1,"type":"crypto"},"eth":{"name":"Ether","unit":"ETH","value":18.801,"type":"crypto"},"ltc":{"name":"Litecoin","unit":"LTC","value":573.745,"type":"crypto"},"bch":{"name":"Bitcoin Cash","unit":"BCH","value":179.527,"type":"crypto"},"bnb":{"name":"Binance Coin","unit":"BNB","value":165.406,"type":"crypto"},"eos":{"name":"EOS","unit":"EOS","value":53351.744,"type":"crypto"},"xrp":{"name":"XRP","unit":"XRP","value":67207.986,"type":"crypto"},"xlm":{"name":"Lumens","unit":"XLM","value":336090.815,"type":"crypto"},"link":{"name":"Chainlink","unit":"LINK","value":2790.99,"type":"crypto"},"dot":{"name":"Polkadot","unit":"DOT","value":5794.625,"type":"crypto"},"yfi":{"name":"Yearn.finance","unit":"YFI","value":4.966,"type":"crypto"},"usd":{"name":"US Dollar","unit":"$","value":41819.856,"type":"fiat"},"aed":{"name":"United Arab Emirates Dirham","unit":"DH","value":153579.24,"type":"fiat"},"ars":{"name":"Argentine Peso","unit":"$","value":1.5327290981E7,"type":"fiat"},"aud":{"name":"Australian Dollar","unit":"A$","value":63701.636,"type":"fiat"},"bdt":{"name":"Bangladeshi Taka","unit":"৳","value":4598657.474,"type":"fiat"},"bhd":{"name":"Bahraini Dinar","unit":"BD","value":15761.903,"type":"fiat"},"bmd":{"name":"Bermudian Dollar","unit":"$","value":41819.856,"type":"fiat"},"brl":{"name":"Brazil Real","unit":"R$","value":206556.634,"type":"fiat"},"cad":{"name":"Canadian Dollar","unit":"CA$","value":56835.401,"type":"fiat"},"chf":{"name":"Swiss Franc","unit":"Fr.","value":36645.61,"type":"fiat"},"clp":{"name":"Chilean Peso","unit":"CLP$","value":3.6927769505E7,"type":"fiat"},"cny":{"name":"Chinese Yuan","unit":"¥","value":300153.654,"type":"fiat"},"czk":{"name":"Czech Koruna","unit":"Kč","value":947600.347,"type":"fiat"},"dkk":{"name":"Danish Krone","unit":"kr.","value":289229.513,"type":"fiat"},"eur":{"name":"Euro","unit":"€","value":38787.833,"type":"fiat"},"gbp":{"name":"British Pound Sterling","unit":"£","value":33332.307,"type":"fiat"},"hkd":{"name":"Hong Kong Dollar","unit":"HK$","value":326648.624,"type":"fiat"},"huf":{"name":"Hungarian Forint","unit":"Ft","value":1.4847928912E7,"type":"fiat"},"idr":{"name":"Indonesian Rupiah","unit":"Rp","value":6.52423787901E8,"type":"fiat"},"ils":{"name":"Israeli New Shekel","unit":"₪","value":154962.766,"type":"fiat"},"inr":{"name":"Indian Rupee","unit":"₹","value":3487625.505,"type":"fiat"},"jpy":{"name":"Japanese Yen","unit":"¥","value":6083576.315,"type":"fiat"},"krw":{"name":"South Korean Won","unit":"₩","value":5.5024642656E7,"type":"fiat"},"kwd":{"name":"Kuwaiti Dinar","unit":"KD","value":12895.027,"type":"fiat"},"lkr":{"name":"Sri Lankan Rupee","unit":"Rs","value":1.3690408443E7,"type":"fiat"},"mmk":{"name":"Burmese Kyat","unit":"K","value":8.7794804623E7,"type":"fiat"},"mxn":{"name":"Mexican Peso","unit":"MX$","value":727202.93,"type":"fiat"},"myr":{"name":"Malaysian Ringgit","unit":"RM","value":195905.116,"type":"fiat"},"ngn":{"name":"Nigerian Naira","unit":"₦","value":3.3043123054E7,"type":"fiat"},"nok":{"name":"Norwegian Krone","unit":"kr","value":458550.918,"type":"fiat"},"nzd":{"name":"New Zealand Dollar","unit":"NZ$","value":68239.759,"type":"fiat"},"php":{"name":"Philippine Peso","unit":"₱","value":2323378.814,"type":"fiat"},"pkr":{"name":"Pakistani Rupee","unit":"₨","value":1.1858350294E7,"type":"fiat"},"pln":{"name":"Polish Zloty","unit":"zł","value":168412.785,"type":"fiat"},"rub":{"name":"Russian Ruble","unit":"₽","value":3766359.071,"type":"fiat"},"sar":{"name":"Saudi Riyal","unit":"SR","value":156850.222,"type":"fiat"},"sek":{"name":"Swedish Krona","unit":"kr","value":438558.35,"type":"fiat"},"sgd":{"name":"Singapore Dollar","unit":"S$","value":56137.176,"type":"fiat"},"thb":{"name":"Thai Baht","unit":"฿","value":1493024.615,"type":"fiat"},"try":{"name":"Turkish Lira","unit":"₺","value":1214331.573,"type":"fiat"},"twd":{"name":"New Taiwan Dollar","unit":"NT$","value":1316269.438,"type":"fiat"},"uah":{"name":"Ukrainian hryvnia","unit":"₴","value":1547079.163,"type":"fiat"},"vef":{"name":"Venezuelan bolívar fuerte","unit":"Bs.F","value":4187.422,"type":"fiat"},"vnd":{"name":"Vietnamese đồng","unit":"₫","value":1.014922020096E9,"type":"fiat"},"zar":{"name":"South African Rand","unit":"R","value":793149.706,"type":"fiat"},"xdr":{"name":"IMF Special Drawing Rights","unit":"XDR","value":31450.079,"type":"fiat"},"xag":{"name":"Silver - Troy Ounce","unit":"XAG","value":1824.024,"type":"commodity"},"xau":{"name":"Gold - Troy Ounce","unit":"XAU","value":21.06,"type":"commodity"},"bits":{"name":"Bits","unit":"μBTC","value":1000000,"type":"crypto"},"sats":{"name":"Satoshi","unit":"sats","value":100000000,"type":"crypto"}}}
                """
  val priceChart: String    = """
{"prices":[[1702080000000,1.6446899669484214e-05],[1702166400000,1.9116562906218465e-05],[1702252800000,1.907096268974158e-05],[1702339200000,1.793183990141821e-05],[1702425600000,2.007992246841523e-05],[1702476909000,1.895741967034403e-05]],"market_caps":[[1702080000000,962.8363048250048],[1702166400000,1130.2772632375334],[1702252800000,1112.6226095555962],[1702339200000,1056.405418156789],[1702425600000,1169.6932210440375],[1702476909000,1112.06422242096]],"total_volumes":[[1702080000000,37.05462537510952],[1702166400000,36.88409762749114],[1702252800000,31.872659876055597],[1702339200000,28.975784486778313],[1702425600000,50.43927040460545],[1702476909000,36.16062757872036]]}
                """
}
