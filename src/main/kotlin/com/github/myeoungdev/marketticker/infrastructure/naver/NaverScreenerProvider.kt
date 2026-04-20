package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ScreenerProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenedTicker
import com.github.myeoungdev.marketticker.domain.model.screener.ScreenerPreset
import com.github.myeoungdev.marketticker.domain.model.screener.screenerLabelKo

class NaverScreenerProvider(
    private val client: NaverClient = NaverClient()
) : ScreenerProvider {

    override fun getScreen(market: MarketType, preset: ScreenerPreset, limit: Int): List<ScreenedTicker> {
        return when (market) {
            MarketType.KOREA,
            MarketType.KOSPI,
            MarketType.KOSDAQ -> getDomesticScreen(preset, limit)

            MarketType.USA,
            MarketType.NASDAQ,
            MarketType.NYSE,
            MarketType.SHANGHAI,
            MarketType.HONG_KONG,
            MarketType.TOKYO,
            MarketType.VIETNAM -> getForeignScreen(market, preset, limit)

            MarketType.UPBIT,
            MarketType.BITHUMB -> getCryptoScreen(market, preset, limit)

            MarketType.UNKNOWN -> emptyList()
        }
    }

    private fun getDomesticScreen(preset: ScreenerPreset, limit: Int): List<ScreenedTicker> {
        val orderType = when (preset) {
            ScreenerPreset.SEARCH_TOP -> "searchTop"
            ScreenerPreset.PRICE_TOP -> "priceTop"
            ScreenerPreset.MARKET_CAP -> "marketSum"
            ScreenerPreset.UP -> "up"
            ScreenerPreset.DOWN -> "down"
        }

        return client.fetchDomesticMarketStockDefault("KRX", "ALL", orderType, 0, limit).map { item ->
            val marketType = when (item.sosok) {
                "0" -> MarketType.KOSPI
                "1" -> MarketType.KOSDAQ
                else -> MarketType.UNKNOWN
            }

            ScreenedTicker(
                ticker = Ticker(
                    symbol = item.itemcode,
                    tradingSymbol = item.itemcode,
                    name = item.itemname,
                    marketType = marketType,
                    nationCode = "KOR",
                    nationName = "대한민국"
                ),
                sector = marketType.displayName,
                industry = preset.labelKo,
                exchange = item.marketStatus,
                marketCap = item.marketSum,
                pe = item.per,
                price = item.nowPrice,
                change = item.prevChangeRate,
                volume = item.tradeVolume,
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
            MarketType.VIETNAM -> "VNM"
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
                MarketType.VIETNAM -> MarketType.VIETNAM
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
                change = item.fluctuationsRatio,
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
                change = formatDecimal(item.changeRate),
                volume = formatDecimal(item.accumulatedTradingVolume, 6),
                signalLabel = preset.labelEn
            )
        }
    }

    private fun formatDecimal(value: Double, digits: Int = 2): String {
        return "%.${digits}f".format(java.util.Locale.US, value)
    }
}
