package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
data class NaverRealtimeResponse (
    val pollingInterval: Long = 0,
    val time: String? = null,
    val datas: List<NaverRealtimeItem> = emptyList()
)


data class NaverRealtimeItem(
    // 종목 코드 (005930)
    val itemCode: String,

    // 종목명 (삼성전자)
    val stockName: String,

    // 현재가 ("103,400") -> 콤마 포함 String
    val closePrice: String,

    // 등락률 ("2.58" 또는 "-1.5") -> 퍼센트
    val fluctuationsRatio: String,

    // 전일 대비 ("2,600")
    val compareToPreviousClosePrice: String,

    // 전일 대비 상태 (상승, 하락, 보합)
    val compareToPreviousPrice: CompareStatus? = null,

    // 거래량 ("10,079,219")
    val accumulatedTradingVolume: String
) {

    fun toTickerPrice(): TickerPrice {
        return TickerPrice(
            symbol = itemCode,
            price = parseDouble(closePrice),
            changeRate = parseDouble(fluctuationsRatio),
            volume = parseDouble(accumulatedTradingVolume).toLong(),
            marketType = "KOREA"
        )
    }

    // 헬퍼 함수: 콤마 제거 후 Double 변환 (실패 시 0.0)
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