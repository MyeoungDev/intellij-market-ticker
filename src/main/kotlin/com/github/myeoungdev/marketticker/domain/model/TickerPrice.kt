package com.github.myeoungdev.marketticker.domain.model

/**
 * 화면과 알림 계산에서 사용하는 현재가 스냅샷 모델입니다.
 */
data class TickerPrice(
    val symbol: String,
    val tradingSymbol: String,
    val name: String,
    val previousClosePrice: Double = 0.0,               // 전일 종가
    val openPrice: Double = 0.0,                // 시가
    val highPrice: Double = 0.0,                // 고가
    val lowPrice: Double = 0.0,                 // 저가
    val currentPrice: Double,                   // 현재가
    val priceStatus: PriceStatus,               // 상승, 하락, 보합
    val changeAmount: Double,                   // 전일 대비 변동액 (+2600, -500)
    val changeRate: Double,                     // 전일 대비 등락률 (+2.58, -1.2) - 퍼센트 단위
    val tradeVolume: Long = 0L,                 // 누적 거래량
    val tradeValue: Double = 0.0,               // 누적 거래대금
    val marketStatus: MarketStatus,             // 장 상태 (OPEN, CLOSED)
    val marketType: MarketType,                 // 시장 (KOREA, USA, COIN)
    val currency: CurrencyType,               // 통화 (KRW, USD)
    val nationCode: String?,
    val nationName: String?,
    val overMarketPrice: DomesticAlternativePrice? = null,
    val integratedPrice: DomesticIntegratedPrice? = null
)

data class DomesticAlternativePrice(
    val currentPrice: Double,
    val openPrice: Double = 0.0,
    val highPrice: Double = 0.0,
    val lowPrice: Double = 0.0,
    val changeAmount: Double = 0.0,
    val changeRate: Double = 0.0,
    val tradeVolume: Long = 0L,
    val tradeValue: Double = 0.0,
    val marketStatus: MarketStatus = MarketStatus.UNKNOWN
)

data class DomesticIntegratedPrice(
    val openPrice: Double = 0.0,
    val highPrice: Double = 0.0,
    val lowPrice: Double = 0.0,
    val tradeVolume: Long = 0L,
    val tradeValue: Double = 0.0
)
