package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryLoadState
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryPage
import com.github.myeoungdev.marketticker.domain.model.news.TickerNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.TickerOverviewCard
import com.github.myeoungdev.marketticker.domain.model.news.NewsLoadStatus
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
        assertThat(first.mostViewedState.status).isEqualTo(NewsLoadStatus.SUCCESS)
    }

    @Test
    fun `뉴스 홈은 전달된 페이지 크기를 provider에 넘긴다`() = runBlocking {
        val provider = FakeNewsProvider()
        val service = NewsFacadeService(provider)

        service.loadNewsHome(pageSize = 20, forceRefresh = true)

        assertThat(provider.headlinePageSizes).containsExactly(20)
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
    fun `카테고리 페이지는 페이지별로 캐시되고 결과 상태를 전달한다`() = runBlocking {
        val provider = FakeNewsProvider()
        val service = NewsFacadeService(provider)

        val first = service.loadNewsCategoryPage("MAINNEWS", page = 2, pageSize = 15)
        val second = service.loadNewsCategoryPage("MAINNEWS", page = 2, pageSize = 15)

        assertThat(first).isEqualTo(second)
        assertThat(first.state.status).isEqualTo(NewsLoadStatus.SUCCESS)
        assertThat(provider.categoryCalls.get()).isEqualTo(1)
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

    @Test
    fun `실패한 카테고리 페이지는 캐시하지 않고 다음 요청에서 재시도한다`() = runBlocking {
        val provider = FakeNewsProvider(categoryPageFailures = 1)
        val service = NewsFacadeService(provider)

        val failed = service.loadNewsCategoryPage("MAINNEWS", page = 2)
        val recovered = service.loadNewsCategoryPage("MAINNEWS", page = 2)

        assertThat(failed.state.status).isEqualTo(NewsLoadStatus.FAILED)
        assertThat(recovered.state.status).isEqualTo(NewsLoadStatus.SUCCESS)
        assertThat(provider.categoryCalls.get()).isEqualTo(1)
    }

    @Test
    fun `실패한 뉴스 홈은 캐시하지 않고 다음 요청에서 재시도한다`() = runBlocking {
        val provider = FakeNewsProvider(homeFailures = 1)
        val service = NewsFacadeService(provider)

        val failed = service.loadNewsHome()
        val recovered = service.loadNewsHome()

        assertThat(failed.headlines.categoryStates["MAINNEWS"]?.status).isEqualTo(NewsLoadStatus.FAILED)
        assertThat(recovered.headlines.categoryStates).isEmpty()
        assertThat(provider.headlineCalls.get()).isEqualTo(2)
    }

    private class FakeNewsProvider(
        private val delayMillis: Long = 0L,
        private val categoryPageFailures: Int = 0,
        private val homeFailures: Int = 0
    ) : NewsProvider {
        val headlineCalls = AtomicInteger()
        val headlinePageSizes = mutableListOf<Int>()
        val mostViewedCalls = AtomicInteger()
        val tickerCalls = AtomicInteger()
        val categoryCalls = AtomicInteger()
        private val categoryPageFailureCount = AtomicInteger()
        private val homeFailureCount = AtomicInteger()

        override fun getHeadlineNews(pageSize: Int): HeadlineNewsBundle {
            headlineCalls.incrementAndGet()
            headlinePageSizes += pageSize
            if (homeFailureCount.getAndIncrement() < homeFailures) {
                return HeadlineNewsBundle(
                    headlines = emptyMap(),
                    categoryStates = mapOf("MAINNEWS" to NewsCategoryLoadState(NewsLoadStatus.FAILED))
                )
            }
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

        override fun getCategoryNews(categoryKey: String, page: Int, pageSize: Int): List<NewsArticle> {
            categoryCalls.incrementAndGet()
            val pageLabel = "p$page"
            return listOf(
                sampleArticle("${categoryKey.lowercase()}-$pageLabel-1", "${categoryKey} ${page}"),
                sampleArticle("${categoryKey.lowercase()}-$pageLabel-2", "${categoryKey} ${page} - 2")
            ).take(pageSize)
        }

        override fun getCategoryNewsPage(categoryKey: String, page: Int, pageSize: Int): NewsCategoryPage {
            if (categoryPageFailureCount.getAndIncrement() < categoryPageFailures) {
                return NewsCategoryPage(state = NewsCategoryLoadState(NewsLoadStatus.FAILED))
            }
            return super.getCategoryNewsPage(categoryKey, page, pageSize)
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
