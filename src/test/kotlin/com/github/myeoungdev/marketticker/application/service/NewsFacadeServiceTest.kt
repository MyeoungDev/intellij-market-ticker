package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.TickerNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard
import com.github.myeoungdev.marketticker.application.provider.NewsProvider
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class NewsFacadeServiceTest {

    @Test
    fun `뉴스 홈은 TTL 내에서 캐시를 재사용한다`() = runBlocking {
        val provider = FakeNewsProvider()
        val service = NewsFacadeService(provider)

        val first = service.loadNewsHome()
        val second = service.loadNewsHome()

        assertThat(first).isEqualTo(second)
        assertThat(provider.headlineCalls.get()).isEqualTo(1)
        assertThat(provider.mostViewedCalls.get()).isEqualTo(1)
    }

    @Test
    fun `강제 새로고침은 캐시를 우회한다`() = runBlocking {
        val provider = FakeNewsProvider()
        val service = NewsFacadeService(provider)

        service.loadNewsHome()
        service.loadNewsHome(forceRefresh = true)

        assertThat(provider.headlineCalls.get()).isEqualTo(2)
        assertThat(provider.mostViewedCalls.get()).isEqualTo(2)
    }

    @Test
    fun `같은 종목에 대한 동시 요청은 하나의 provider 호출로 병합된다`() = runBlocking {
        val provider = FakeNewsProvider(delayMillis = 150)
        val service = NewsFacadeService(provider)
        val ticker = Ticker("NVDA", "NVDA.O", "엔비디아", MarketType.NASDAQ, "USA", "미국")

        val results = awaitAll(
            async(Dispatchers.Default) { service.loadTickerNewsSummary(ticker) },
            async(Dispatchers.Default) { service.loadTickerNewsSummary(ticker) }
        )

        assertThat(results[0].articles).hasSize(1)
        assertThat(results[1].articles).hasSize(1)
        assertThat(provider.tickerCalls.get()).isEqualTo(1)
    }

    private class FakeNewsProvider(
        private val delayMillis: Long = 0L
    ) : NewsProvider {
        val headlineCalls = AtomicInteger()
        val mostViewedCalls = AtomicInteger()
        val tickerCalls = AtomicInteger()

        override fun getHeadlineNews(): HeadlineNewsBundle {
            headlineCalls.incrementAndGet()
            return HeadlineNewsBundle(
                headlines = mapOf("MAINNEWS" to listOf(sampleArticle("main-1", "메인 뉴스"))),
                worldNews = listOf(sampleArticle("world-1", "해외 뉴스")),
                moneyStories = listOf(sampleArticle("money-1", "머니 스토리"))
            )
        }

        override fun getMostViewedNews(limit: Int): List<NewsArticle> {
            mostViewedCalls.incrementAndGet()
            return listOf(sampleArticle("rank-1", "많이 본 뉴스")).take(limit)
        }

        override fun getTickerNews(ticker: Ticker): TickerNewsBundle {
            tickerCalls.incrementAndGet()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            return TickerNewsBundle(
                overviewCard = TickerOverviewCard(
                    title = "${ticker.name} / ${ticker.tradingSymbol}",
                    metaPrimary = "NASDAQ · 반도체",
                    metaSecondary = "시총 1조원",
                    primaryMetrics = "PER 10배 · PBR 2배",
                    secondaryMetrics = "52주 1 - 2",
                    summary = "요약",
                    siteUrl = "https://example.com"
                ),
                articles = listOf(sampleArticle("ticker-1", "${ticker.name} 뉴스"))
            )
        }

        override fun searchNews(query: String, page: Int, pageSize: Int): List<NewsArticle> {
            return listOf(sampleArticle("search-1", query)).take(pageSize)
        }

        private fun sampleArticle(id: String, title: String): NewsArticle {
            return NewsArticle(
                id = id,
                title = title,
                summary = "summary",
                source = "source",
                publishedAt = "202603151200",
                url = "https://example.com/$id"
            )
        }
    }
}
