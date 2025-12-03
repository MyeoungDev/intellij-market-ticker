package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.github.myeoungdev.marketticker.domain.model.PriceStatus
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverRealTimeStockPriceResponse(
    val pollingInterval: Long = 0,
    val time: String? = null,
    val datas: List<NaverStockPrice> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverStockPrice(
    // 종목 코드 (005930)
    val itemCode: String,

    // 종목명 (삼성전자)
    val stockName: String,

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

    // 전일 대비 상태 (상승, 하락, 보합)
    val compareToPreviousPrice: CompareStatus? = null,

    // 거래량 ("10,079,219")
    val accumulatedTradingVolume: String,

    // 거래 대금 ("1,032,919백만")
    val accumulatedTradingValue: String,

    // 시장 상태
    val marketStatus: String,

    // 국제 증권 식별 코드
    val isinCode: String?,

    val currencyType: CurrencyType
) {

    // TODO: toDomain Logic
    fun toTickerPrice(): TickerPrice {
        return TickerPrice(
            symbol = itemCode,
            name = stockName,
            previousClosePrice = closePrice - compareToPreviousClosePrice,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            currentPrice = closePrice,
            priceStatus = PriceStatus.from(compareToPreviousClosePrice),
            changeAmount = compareToPreviousClosePrice,
            changeRate = fluctuationsRatio,
            tradeVolume = accumulatedTradingVolume,
            tradeValue = accumulatedTradingValue,
            marketStatus = MarketStatus.of(marketStatus)
        )
    }


    // TODO: Extract Util Class
    private fun parseDouble(value: String?): Double {
        return value?.replace(",", "")?.toDoubleOrNull() ?: 0.0
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CompareStatus(
    val code: String, // "2" (상승), "5" (하락) 등
    val text: String, // "상승", "하락"
    val name: String  // "RISING", "FALLING"
)

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
