package com.github.myeoungdev.marketticker.domain.model.theme

import com.github.myeoungdev.marketticker.domain.model.Ticker
import java.time.Instant

data class ThemeLeader(val ticker: Ticker, val changeRate: Double)

data class ThemeSummary(
    val code: String,
    val name: String,
    val changeRate: Double,
    val risingCount: Int,
    val fallingCount: Int,
    val unchangedCount: Int,
    val updatedAt: Instant,
    val leaders: List<ThemeLeader>
)

/** 여러 페이지를 모두 조회해 구성한 결과이며, 서버의 단일 시점 스냅샷은 아닙니다. */
data class ThemeSummarySnapshot(
    val gainers: List<ThemeSummary>,
    val losers: List<ThemeSummary>,
    val fetchedAt: Instant
)

data class ThemeConstituent(
    val ticker: Ticker,
    val price: Double? = null,
    val changeRate: Double? = null,
    val volume: Long? = null
)
