package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.CurrencyResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinOverviewInfo
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinProfileInfo
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDomesticStockDetail
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockBasic
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockIndustry
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockListedInfo
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockMetric
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverForeignStockSummaryBlock
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverNewsArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchArticle
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.StockExchangeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("하단 종목 뉴스 요약 포맷터 테스트")
class StockNewsSummaryFormatterTest {

    @Nested
    @DisplayName("1. 회사 개요 카드 조합")
    inner class OverviewCardTest {

        @Test
        fun `overview와 basic이 함께 있으면 핵심 지표를 묶어 카드로 만든다`() {
            val card = StockNewsSummaryFormatter.buildOverviewCard(
                ticker = createForeignTicker(),
                overview = createOverview(),
                basic = createBasic()
            )

            assertThat(card).isNotNull
            assertThat(card?.title).contains("엔비디아")
            assertThat(card?.metaPrimary).contains("NASDAQ", "반도체")
            assertThat(card?.metaSecondary).contains("6,652조 1,827억원")
            assertThat(card?.primaryMetrics).contains("PER 37.73배", "PBR 28.68배")
            assertThat(card?.secondaryMetrics).contains("52주 86.62 - 212.19", "배당 0.02%")
            assertThat(card?.summary).doesNotContain("<br>")
            assertThat(card?.siteUrl).isEqualTo("https://www.nvidia.com/")
        }

        @Test
        fun `basic만 있어도 숫자 중심 카드가 만들어진다`() {
            val card = StockNewsSummaryFormatter.buildOverviewCard(
                ticker = createForeignTicker(),
                overview = null,
                basic = createBasic()
            )

            assertThat(card).isNotNull
            assertThat(card?.title).contains("엔비디아")
            assertThat(card?.metaPrimary).contains("NASDAQ", "반도체")
            assertThat(card?.metaSecondary).contains("6,652조 1,827억원")
            assertThat(card?.primaryMetrics).isEqualTo("PER 37.73배 · PBR 28.68배")
            assertThat(card?.siteUrl).isEqualTo("https://m.stock.naver.com/worldstock/stock/NVDA.O")
        }

        @Test
        fun `입력이 모두 없으면 카드를 만들지 않는다`() {
            val card = StockNewsSummaryFormatter.buildOverviewCard(
                ticker = createForeignTicker(),
                overview = null,
                basic = null
            )

            assertThat(card).isNull()
        }

        @Test
        fun `국내 종목은 상세 API 기반으로 개요 카드를 만든다`() {
            val card = StockNewsSummaryFormatter.buildOverviewCard(
                createDomesticTicker(),
                overview = null,
                basic = null,
                domesticDetail = createDomesticDetail(),
                research = createDomesticResearch()
            )

            assertThat(card).isNotNull
            assertThat(card?.title).isEqualTo("흥아해운")
            assertThat(card?.metaPrimary).contains("대한민국", "KOSPI", "해운사")
            assertThat(card?.metaSecondary).contains("시총")
            assertThat(card?.primaryMetrics).contains("PER 20.48배", "PBR 2.46배", "EPS 114.0")
            assertThat(card?.secondaryMetrics).contains("52주 1414 - 3290")
            assertThat(card?.summary).contains("해운기업")
            assertThat(card?.summary?.length).isLessThanOrEqualTo(183)
            assertThat(card?.siteUrl).isNull()
        }

        @Test
        fun `코인은 상세 시세 기반으로 개요 카드를 만든다`() {
            val card = StockNewsSummaryFormatter.buildOverviewCard(
                ticker = createCryptoTicker(),
                overview = null,
                basic = null,
                coinOverview = createCoinOverview()
            )

            assertThat(card).isNotNull
            assertThat(card?.title).contains("비트코인", "Bitcoin")
            assertThat(card?.metaPrimary).contains("업비트", "UPBIT")
            assertThat(card?.metaSecondary).contains("시총")
            assertThat(card?.primaryMetrics).contains("+0.08% · +87,000 KRW", "김프 -1.49%")
            assertThat(card?.secondaryMetrics).isEqualTo("52주 89,000,000 - 179,869,000")
            assertThat(card?.summary).contains("블록체인")
        }
    }

    @Nested
    @DisplayName("2. 뉴스 헤드라인 병합")
    inner class MergeArticlesTest {

        @Test
        fun `국내와 해외 뉴스를 병합할 때 중복 제거 후 최신순으로 정렬한다`() {
            val domestic = listOf(
                article("A", "https://n.news/1", "202603112349"),
                article("B", "https://n.news/2", "202603112251"),
                article("A", "https://n.news/1", "202603112349")
            )
            val foreign = listOf(
                article("C", "https://stock.naver.com/news/worldnews/3", "202603112400"),
                article("D", "https://stock.naver.com/news/worldnews/4", "202603111200")
            )

            val merged = StockNewsSummaryFormatter.mergeArticles(domestic, foreign, limit = 5)

            assertThat(merged.map { it.title }).containsExactly("C", "A", "B", "D")
        }

        @Test
        fun `병합 결과는 최대 개수까지만 노출한다`() {
            val domestic = (1..4).map { idx -> article("D$idx", "https://n.news/$idx", "20260311${40 + idx}") }
            val foreign = (1..4).map { idx -> article("F$idx", "https://stock.naver.com/news/worldnews/$idx", "20260311${30 + idx}") }

            val merged = StockNewsSummaryFormatter.mergeArticles(domestic, foreign, limit = 5)

            assertThat(merged).hasSize(5)
        }
    }

    private fun createOverview(): NaverForeignStockOverview {
        return NaverForeignStockOverview(
            companyName = "엔비디아",
            companyNameEng = "NVIDIA Corp",
            summary = "엔비디아는 풀스택 컴퓨팅 인프라 회사다.<br>컴퓨팅 및 네트워킹과 그래픽 부문으로 운영한다.",
            summaries = NaverForeignStockSummaryBlock(
                summary = "엔비디아는 풀스택 컴퓨팅 인프라 회사다.<br>컴퓨팅 및 네트워킹과 그래픽 부문으로 운영한다.",
                representativeName = "Jen-Hsun Huang",
                url = "https://www.nvidia.com/"
            ),
            industry = NaverForeignStockIndustry(industryGroupKor = "반도체"),
            stockItemListedInfo = NaverForeignStockListedInfo(
                stockExchange = "NASDAQ",
                marketValueKrw = "6,678조 2,551억원"
            )
        )
    }

    private fun createForeignTicker(): Ticker {
        return Ticker(
            symbol = "NVDA",
            tradingSymbol = "NVDA.O",
            name = "엔비디아",
            marketType = MarketType.NASDAQ,
            nationCode = "USA",
            nationName = "미국"
        )
    }

    private fun createDomesticTicker(): Ticker {
        return Ticker(
            symbol = "005930",
            tradingSymbol = "005930",
            name = "삼성전자",
            marketType = MarketType.KOSPI,
            nationCode = "KOR",
            nationName = "대한민국"
        )
    }

    private fun createCryptoTicker(): Ticker {
        return Ticker(
            symbol = "BTC",
            tradingSymbol = "BTC_KRW_UPBIT",
            name = "비트코인",
            marketType = MarketType.UPBIT,
            nationCode = "KOR",
            nationName = "대한민국"
        )
    }

    private fun createDomesticResearch(): NaverResearchArticle {
        return NaverResearchArticle(
            itemCode = "005930",
            itemName = "삼성전자",
            title = "주가 매력도가 더 높아졌다",
            content = "<p><strong>동사에 대한 투자의견과 목표주가 유지</strong></p><p>삼성전자는 메모리 업황의 선행 지표가 여전히 견조하고, 가격 매력도와 배당 수익률이 유의미하게 높아졌다.</p>",
            brokerName = "미래에셋증권",
            writeDate = "2026-03-10",
            endUrl = "https://stock.pstatic.net/report.pdf",
            opinion = "매수",
            goalPrice = "275000",
            prevGoalPrice = "173500"
        )
    }

    private fun createDomesticDetail(): NaverDomesticStockDetail {
        return NaverDomesticStockDetail(
            itemcode = "003280",
            itemname = "흥아해운",
            marketSum = "561392000000",
            per = "20.48",
            eps = "114.0",
            pbr = "2.46",
            high52week = "3290",
            low52week = "1414",
            upJongName = "해운사",
            comment1 = "동사는 1961년 설립, 1976년 유가증권시장 상장한 해운기업으로 국내 7개, 해외 4개 계열회사를 보유하고 있음.",
            comment2 = "아시아 지역 내 액체석유화학제품의 해상운송과 부동산 임대업을 병행하며, 연결재무제표 기준 케미컬탱커 사업이 86.56%, 부동산 임대 등 실적이 13.44%임.",
            comment3 = "디지털 전환을 통한 스마트 해운 역량 강화, 친환경 선박 도입, 대체 연료 전환 등 ESG 경영을 추진하며 종합물류기업 전환을 모색하고 있음."
        )
    }

    private fun createBasic(): NaverForeignStockBasic {
        return NaverForeignStockBasic(
            stockName = "엔비디아",
            stockNameEng = "NVIDIA Corp",
            stockExchangeName = "NASDAQ",
            industryCodeType = NaverForeignStockIndustry(industryGroupKor = "반도체"),
            closePrice = "185.62",
            fluctuationsRatio = "0.47",
            currencyType = CurrencyResponse(code = "USD", text = "US dollar", name = "USD"),
            endUrl = "https://m.stock.naver.com/worldstock/stock/NVDA.O",
            stockExchangeType = StockExchangeType(
                code = "NSQ",
                zoneId = "EST5EDT",
                nationType = "USA",
                delayTime = 0,
                startTime = "0930",
                endTime = "1600",
                closePriceSendTime = "2031",
                nameKor = "나스닥 증권거래소",
                nameEng = "NASDAQ Stock Exchange",
                nationCode = "USA",
                nationName = "미국",
                stockType = "worldstock",
                name = "NASDAQ"
            ),
            stockItemTotalInfos = listOf(
                NaverForeignStockMetric(code = "marketValue", value = "4조 5,106억 USD", valueDesc = "6,652조 1,827억원"),
                NaverForeignStockMetric(code = "highPriceOf52Weeks", value = "212.19"),
                NaverForeignStockMetric(code = "lowPriceOf52Weeks", value = "86.62"),
                NaverForeignStockMetric(code = "per", value = "37.73배"),
                NaverForeignStockMetric(code = "pbr", value = "28.68배"),
                NaverForeignStockMetric(code = "dividendYieldRatio", value = "0.02%")
            )
        )
    }

    private fun createCoinOverview(): NaverCoinOverview {
        return NaverCoinOverview(
            fqnfTicker = "BTC_KRW_UPBIT",
            nfTicker = "BTC",
            exchangeTicker = "BTC",
            krName = "비트코인",
            enName = "Bitcoin",
            exchangeType = "UPBIT",
            exchangeName = "업비트",
            tradePrice = 104491000.0,
            change = "RISING",
            changeRate = 0.08,
            changeValue = 87000.0,
            koreaTradedAt = "2026-03-14T20:18:41.334",
            krwPremiumRate = -1.49,
            totalInfos = listOf(
                NaverCoinOverviewInfo(code = "lowest52weekPrice", value = 89000000.0),
                NaverCoinOverviewInfo(code = "highest52weekPrice", value = 179869000.0)
            ),
            profileInfo = NaverCoinProfileInfo(
                contentKr = "비트코인은 블록체인 기술 기반의 대표적인 디지털 자산입니다.",
                marketCap = 2122689996543607.0
            )
        )
    }

    private fun article(title: String, url: String, datetime: String): NaverNewsArticle {
        return NaverNewsArticle(
            title = title,
            url = url,
            datetime = datetime
        )
    }
}
