package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ScreenerProvider
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.github.myeoungdev.marketticker.domain.model.screener.screenerLabelKo

class NaverScreenerProvider(
    private val client: NaverClient = NaverClient()
) : ScreenerProvider {

    override fun getScreen(
        market: MarketType,
        preset: ScreenerPreset,
        limit: Int,
        domesticTradeType: DomesticTradeType
    ): List<ScreenedTicker> {
        return when (market) {
            MarketType.KOREA,
            MarketType.KOSPI,
            MarketType.KOSDAQ -> getDomesticScreen(preset, limit, domesticTradeType)

            MarketType.USA,
            MarketType.NASDAQ,
            MarketType.NYSE,
            MarketType.SHANGHAI,
            MarketType.HONG_KONG,
            MarketType.TOKYO -> getForeignScreen(market, preset, limit)

            MarketType.VIETNAM -> emptyList()

            MarketType.UPBIT,
            MarketType.BITHUMB -> getCryptoScreen(market, preset, limit)

            MarketType.UNKNOWN -> emptyList()
        }
    }

    private fun getDomesticScreen(
        preset: ScreenerPreset,
        limit: Int,
        domesticTradeType: DomesticTradeType
    ): List<ScreenedTicker> {
        val orderType = when (preset) {
            ScreenerPreset.SEARCH_TOP -> "searchTop"
            ScreenerPreset.PRICE_TOP -> "priceTop"
            ScreenerPreset.MARKET_CAP -> "marketSum"
            ScreenerPreset.UP -> "up"
            ScreenerPreset.DOWN -> "down"
        }

        val tradeType = domesticTradeType.code
        val items = client.fetchDomesticMarketStockDefault(tradeType, "ALL", orderType, 0, limit)
            .ifEmpty {
                if (preset == ScreenerPreset.SEARCH_TOP) {
                    client.fetchDomesticMarketStockDefault(tradeType, "ALL", "quantTop", 0, limit)
                } else {
                    emptyList()
                }
            }

        return items.map { item ->
            val itemCode = item.itemcode.orEmpty()
            val itemName = item.itemname.orEmpty()
            val nowPrice = item.nowPrice.orEmpty()
            val prevChangeRate = item.prevChangeRate.orEmpty()
            val marketType = when (item.sosok.orEmpty()) {
                "0" -> MarketType.KOSPI
                "1" -> MarketType.KOSDAQ
                else -> MarketType.UNKNOWN
            }

            ScreenedTicker(
                ticker = Ticker(
                    symbol = itemCode,
                    tradingSymbol = itemCode,
                    name = itemName,
                    marketType = marketType,
                    nationCode = "KOR",
                    nationName = "대한민국"
                ),
                sector = marketType.displayName,
                industry = preset.labelKo,
                exchange = item.marketStatus.orEmpty(),
                marketCap = item.marketSum.orEmpty(),
                pe = item.per.orEmpty(),
                price = nowPrice,
                changeRate = prevChangeRate,
                changeAmount = formatChangeAmount(nowPrice, prevChangeRate),
                volume = item.tradeVolume.orEmpty(),
                signalLabel = preset.labelEn
            )
        }
    }

    private fun getForeignScreen(market: MarketType, preset: ScreenerPreset, limit: Int): List<ScreenedTicker> {
        val nation = when (market) {
            MarketType.USA,
            MarketType.NASDAQ,
            MarketType.NYSE -> "USA"
            MarketType.SHANGHAI -> "CHN"
            MarketType.HONG_KONG -> "HKG"
            MarketType.TOKYO -> "JPN"
            else -> error("Unsupported foreign screener market: $market")
        }

        val orderType = when (preset) {
            ScreenerPreset.SEARCH_TOP -> "quantTop"
            ScreenerPreset.PRICE_TOP -> "priceTop"
            ScreenerPreset.MARKET_CAP -> "marketValue"
            ScreenerPreset.UP -> "up"
            ScreenerPreset.DOWN -> "down"
        }

        return client.fetchForeignMarketStockGlobal(nation, "ALL", orderType, 0, limit).map { item ->
            val marketType = when (market) {
                MarketType.USA,
                MarketType.NASDAQ,
                MarketType.NYSE -> when (item.stockExchangeType.code) {
                    "NSQ" -> MarketType.NASDAQ
                    "NYS" -> MarketType.NYSE
                    else -> MarketType.UNKNOWN
                }
                MarketType.SHANGHAI -> MarketType.SHANGHAI
                MarketType.HONG_KONG -> MarketType.HONG_KONG
                MarketType.TOKYO -> MarketType.TOKYO
                else -> MarketType.UNKNOWN
            }

            ScreenedTicker(
                ticker = Ticker(
                    symbol = item.reutersCode,
                    tradingSymbol = item.symbolCode.ifBlank { item.reutersCode },
                    name = item.koreanCodeName.ifBlank { item.englishCodeName },
                    marketType = marketType,
                    nationCode = nation,
                    nationName = market.screenerLabelKo()
                ),
                sector = item.stockExchangeType.nameKor.ifBlank { market.screenerLabelKo() },
                industry = item.reutersIndustryName,
                exchange = item.stockExchangeType.code,
                marketCap = item.marketValue,
                pe = "",
                price = item.currentPrice,
                changeRate = item.fluctuationsRatio,
                changeAmount = item.compareToPreviousClosePrice,
                volume = item.accumulatedTradingVolume,
                signalLabel = preset.labelEn
            )
        }
    }

    private fun getCryptoScreen(market: MarketType, preset: ScreenerPreset, limit: Int): List<ScreenedTicker> {
        val exchange = market.name
        val sortType = when (preset) {
            ScreenerPreset.SEARCH_TOP -> "top"
            ScreenerPreset.MARKET_CAP -> "marketValue"
            ScreenerPreset.UP -> "up"
            ScreenerPreset.DOWN -> "down"
            ScreenerPreset.PRICE_TOP -> "top"
        }

        return client.fetchCoinRank(exchange, sortType, page = 1, pageSize = limit).contents.map { item ->
            val marketType = when (market) {
                MarketType.UPBIT -> MarketType.UPBIT
                MarketType.BITHUMB -> MarketType.BITHUMB
                else -> MarketType.UNKNOWN
            }

            ScreenedTicker(
                ticker = Ticker(
                    symbol = item.nfTicker,
                    tradingSymbol = item.exchangeTicker.ifBlank { item.nfTicker },
                    name = item.krName,
                    marketType = marketType,
                    nationCode = "KOR",
                    nationName = "대한민국"
                ),
                sector = item.exchangeName,
                industry = item.enName,
                exchange = item.exchangeType,
                marketCap = formatDecimal(item.marketCap),
                pe = "",
                price = formatDecimal(item.tradePrice),
                changeRate = formatSignedDecimal(item.changeRate),
                changeAmount = formatSignedDecimal(item.changeValue),
                volume = formatDecimal(item.accumulatedTradingVolume, 6),
                signalLabel = preset.labelEn
            )
        }
    }

    private fun formatDecimal(value: Double, digits: Int = 2): String {
        return "%.${digits}f".format(java.util.Locale.US, value)
    }

    private fun formatSignedDecimal(value: Double, digits: Int = 2): String {
        val formatted = formatDecimal(kotlin.math.abs(value), digits)
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun formatChangeAmount(price: String, changeRate: String): String {
        val current = price.parseCommaToDouble()
        val rate = changeRate.parseCommaToDouble()
        if (current == 0.0 || rate == 0.0) return ""
        val previous = current / (1 + rate / 100.0)
        val delta = current - previous
        val sign = when {
            delta > 0 -> "+"
            delta < 0 -> "-"
            else -> ""
        }
        return "$sign${formatDecimal(kotlin.math.abs(delta), 2)}"
    }
}
