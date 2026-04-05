package com.github.myeoungdev.marketticker.infrastructure.naver.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToDouble
import com.github.myeoungdev.marketticker.common.extenion.parseCommaToLong
import com.github.myeoungdev.marketticker.domain.model.*

/**
 * Naver 실시간 시세 응답 DTO입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverRealTimeStockPriceResponse(
    val pollingInterval: Long = 0,
    val time: String? = null,
    val datas: List<NaverStockPrice> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverStockPrice(

    // 종목 코드 (국내: 005930) (해외: APPL)
    @JsonAlias("symbolCode")
    val itemCode: String,

    // 종목명 (삼성전자)
    val stockName: String,

    // 해외 검색을 위한 Code (해외: APPL.O)
    val reutersCode: String?,

    // 주식에 대한 정보
    val stockExchangeType: StockExchangeType,

    // 시가
    val openPrice: String,

    // 고가
    val highPrice: String,

    // 저가
    val lowPrice: String,

    // 현재가 ("103,400") -> 콤마 포함 String
    val closePrice: String,

    // 등락률 ("2.58" 또는 "-1.5") -> 퍼센트
    val fluctuationsRatio: String,

    // 전일 대비 ("2,600")
    val compareToPreviousClosePrice: String,

    // 거래량 ("10,079,219")
    val accumulatedTradingVolume: String,

    // 거래 대금 ("1,032,919백만")
    val accumulatedTradingValue: String,

    // 시장 상태
    val marketStatus: String,

    // 국제 증권 식별 코드
    val isinCode: String?,

    val currencyType: CurrencyResponse
) {

    fun toTickerPrice(): TickerPrice {
        return TickerPrice(
            symbol = itemCode,
            tradingSymbol = reutersCode ?: itemCode,
            name = stockName,
            previousClosePrice = closePrice.parseCommaToDouble() - compareToPreviousClosePrice.parseCommaToDouble(),
            openPrice = openPrice.parseCommaToDouble(),
            highPrice = highPrice.parseCommaToDouble(),
            lowPrice = lowPrice.parseCommaToDouble(),
            currentPrice = closePrice.parseCommaToDouble(),
            priceStatus = PriceStatus.from(compareToPreviousClosePrice.parseCommaToDouble()),
            changeAmount = compareToPreviousClosePrice.parseCommaToDouble(),
            changeRate = fluctuationsRatio.parseCommaToDouble(),
            tradeVolume = accumulatedTradingVolume.parseCommaToLong(),
            tradeValue = accumulatedTradingValue.parseCommaToDouble(),
            marketStatus = MarketStatus.of(marketStatus),
            marketType = stockExchangeType.toMarketType(),
            currency = CurrencyType.of(currencyType.code),
            nationCode = stockExchangeType.nationCode,
            nationName = stockExchangeType.nationName
        )
    }

    private fun StockExchangeType.toMarketType(): MarketType {
        return when (this.code) {
            "KS" -> MarketType.KOSPI
            "KQ" -> MarketType.KOSDAQ
            "NS", "NSQ" -> MarketType.NASDAQ
            "NYS" -> MarketType.NYSE
            else -> MarketType.UNKNOWN
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CompareStatus(
    val code: String, // "2" (상승), "5" (하락) 등
    val text: String, // "상승", "하락"
    val name: String  // "RISING", "FALLING"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StockExchangeType(
    val code: String,                   // "KS",
    val zoneId: String,                 // Asia/Seoul
    val nationType: String,             // KOR
    val delayTime: Long,                // 0
    val startTime: String,              // 0900
    val endTime: String,                // 1530
    val closePriceSendTime: String,     // 1630
    val nameKor: String,                // 코스피
    val nameEng: String,                // KOSPI
    val nationCode: String,             // KOR
    val nationName: String,             // 대한미국
    val stockType: String,              // domestic
    val name: String                    // KOSPI
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CurrencyResponse(
    val code: String,
    val text: String,
    val name: String
)
