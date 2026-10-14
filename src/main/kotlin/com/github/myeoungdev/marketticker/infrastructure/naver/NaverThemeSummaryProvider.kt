package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.application.provider.ThemeSummaryProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeLeader
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummary
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverThemeRankingItem
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

/**
 *  네이버증권 웹 API에서 테마 순위와 구성 종목을 동기 방식으로 조회합니다.
 */
class NaverThemeSummaryProvider(
    private val client: NaverClient = NaverClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val requestTimeout: Duration = Duration.ofSeconds(8),
    private val chainTimeout: Duration = Duration.ofSeconds(30),
    private val nanoTime: () -> Long = System::nanoTime
) : ThemeSummaryProvider {
    init {
        require(requestTimeout > Duration.ZERO && requestTimeout <= Duration.ofSeconds(8))
        require(chainTimeout > Duration.ZERO && chainTimeout <= Duration.ofSeconds(30))
    }

    /**
     * 일간 테마 순위를 커서 페이지로 조회해 상승·하락 상위 3개씩 반환합니다.
     *
     * 페이지당 최대 100개, 최대 5페이지까지 조회하며 각 요청은 최대 8초,
     * 전체 조회는 최대 30초로 제한합니다. 결과는 순차 페이지 응답을 합친 스냅샷이며,
     * 요청 실패·잘못된 응답·페이지 제한 또는 전체 제한 시간 초과는 예외로 전달됩니다.
     */
    override fun getDailySummary(): ThemeSummarySnapshot {
        val startedAt = nanoTime()
        fun remaining(): Duration {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Theme traversal interrupted")
            val remaining = chainTimeout.minusNanos(nanoTime() - startedAt)
            if (remaining <= Duration.ZERO) throw HttpTimeoutException("Naver theme chain deadline exceeded")
            return remaining
        }

        val themes = linkedMapOf<String, ThemeSummary>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        repeat(5) { pageIndex ->
            val page = client.fetchThemeRankingsPage(cursor, minOf(requestTimeout, remaining()))
            remaining()
            page.items.forEach { item ->
                val theme = mapTheme(item)
                val previous = themes[theme.code]
                if (previous == null || theme.updatedAt > previous.updatedAt) themes[theme.code] = theme
            }
            remaining()
            if (!page.hasNext) {
                val items = themes.values.toList()
                val gainers = items.filter { it.changeRate > 0 }.sortedByDescending { it.changeRate }.take(3)
                val losers = items.filter { it.changeRate < 0 }.sortedBy { it.changeRate }.take(3)
                remaining()
                return ThemeSummarySnapshot(gainers, losers, clock.instant())
            }
            check(pageIndex < 4) { "Incomplete Naver themes: five-page cap exceeded" }
            val next = page.cursor
            check(!next.isNullOrBlank() && cursors.add(next)) { "Missing or repeated Naver theme cursor" }
            cursor = next
        }
        error("Incomplete Naver theme traversal")
    }

    /** 거래량순 구성 종목을 최대 20개 조회합니다. 가격·등락률·거래량은 없을 수 있습니다. */
    override fun getThemeMembers(themeCode: String): List<ThemeConstituent> =
        client.fetchThemeMembers(themeCode).map { item ->
            ThemeConstituent(
                ticker = ticker(
                    required(item.itemcode, "member code"), required(item.itemname, "member name"),
                    when (item.sosok) {
                        "0" -> MarketType.KOSPI
                        "1" -> MarketType.KOSDAQ
                        else -> MarketType.KOREA
                    }
                ),
                price = optionalNumber(item.nowPrice),
                changeRate = optionalNumber(item.prevChangeRate),
                volume = item.tradeVolume?.replace(",", "")?.toLongOrNull()?.takeIf { it >= 0 }
            )
        }

    private fun mapTheme(item: NaverThemeRankingItem): ThemeSummary = ThemeSummary(
        code = required(item.code, "code"),
        name = required(item.name, "name"),
        changeRate = checkNotNull(item.changeRate?.toDoubleOrNull()?.takeIf { it.isFinite() }) {
            "Invalid Naver theme changeRate"
        },
        risingCount = count(item.risingCount),
        fallingCount = count(item.fallingCount),
        unchangedCount = count(item.unchangedCount),
        updatedAt = OffsetDateTime.parse(required(item.updatedAt, "updatedAt")).toInstant(),
        leaders = item.topByChangeRate.orEmpty().mapNotNull { leader ->
            val code = leader?.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = leader.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = optionalNumber(leader.value) ?: return@mapNotNull null
            ThemeLeader(ticker(code, name), value)
        }.take(3)
    )

    private fun required(value: String?, field: String): String =
        checkNotNull(value?.takeIf { it.isNotBlank() }) { "Missing Naver theme $field" }

    private fun count(value: String?): Int =
        checkNotNull(value?.toIntOrNull()?.takeIf { it >= 0 }) { "Invalid Naver theme count" }

    private fun optionalNumber(value: String?): Double? =
        value?.replace(",", "")?.trim()?.removeSuffix("%")?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun ticker(code: String, name: String, market: MarketType = MarketType.KOREA) =
        Ticker(code, code, name, market, "KOR", "대한민국")
}
