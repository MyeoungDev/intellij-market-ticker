package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.application.service.PriceHistoryService
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.DomesticTradeType
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.CurrencyType
import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.fixtures.domain.TickerFixtures
import com.github.myeoungdev.marketticker.fixtures.naver.NaverFixtures
import com.github.myeoungdev.marketticker.domain.model.news.NewsLoadStatus
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDiscussionRankingResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverExchangeRateItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchLatestResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverResearchRankingResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCoinOverview
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverCryptoChartResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverRealTimeStockPriceResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDomesticV2Session
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverDomesticV2StockItem
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverSearchResponse
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchCategoryKey
import com.github.myeoungdev.marketticker.infrastructure.naver.dto.ResearchRankingType
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.http.HttpClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2026-01-23
 */
@DisplayName("Naver API 및 Client 통합 테스트")
class NaverClientTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var naverClient: NaverClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
        wireMockServer.start()

        val baseUrl = wireMockServer.baseUrl()

        naverClient = NaverClient(
            client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(500))
                .build(),
            searchBaseUrl = "$baseUrl/search",
            domesticPriceUrl = "$baseUrl/domestic/stock",
            worldPriceUrl = "$baseUrl/worldstock/stock",
            coinPriceUrl = "$baseUrl/coin/price",
            coinOverviewUrl = "$baseUrl/api/coin/price",
            cryptoChartUrl = "$baseUrl/chart/cryptoChartData",
            domesticIndexUrl = "$baseUrl/domestic/index",
            worldIndexUrl = "$baseUrl/worldstock/index",
            marketMetalUrl = "$baseUrl/marketindex/metals",
            marketEnergyUrl = "$baseUrl/marketindex/energy",
            exchangeRateUrl = "$baseUrl/domestic/exchange/List",
            domesticChartUrl = "$baseUrl/chart/domestic/item",
            foreignChartUrl = "$baseUrl/chart/foreign/item",
            researchAggregateUrl = "$baseUrl/research/aggregate",
            researchRecentPopularUrl = "$baseUrl/research/recent-popular",
            industryResearchUrl = "$baseUrl/research/industry-research",
            discussionRankingUrl = "$baseUrl/community/discussion/rankings",
            researchRankingUrl = "$baseUrl/research/ranking",
            newsListUrl = "$baseUrl/news/list",
            newsFocusUrl = "$baseUrl/news/focus",
            worldNewsUrl = "$baseUrl/foreign/news/worldNews",
            moneyStoryUrl = "$baseUrl/content/moneyStory",
            domesticDetailNewsUrl = "$baseUrl/domestic/detail/news",
            domesticStockDetailUrl = "$baseUrl/domestic/detail",
            foreignStockNewsUrl = "$baseUrl/foreign/worldStock/list",
            foreignStockOverviewUrl = "$baseUrl/securityService/stock",
            foreignStockBasicUrl = "$baseUrl/securityService/stock",
            noticeListUrl = "$baseUrl/news/noticeList",
            newsSearchUrl = "$baseUrl/news/search",
            researchLatestV2Url = "$baseUrl/research/latestResearch",
            researchCompanyV2Url = "$baseUrl/research/company/by-items",
            treasuryBaseUrl = "$baseUrl/marketindex/bond/nation"
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Nested
    @DisplayName("1. DTO 매핑 및 JSON 파싱 검증")
    inner class DtoMappingTest {

        @Test
        fun `NaverSearchItem 결과에서 Ticker 도메인 변환 검증`() {
            // Given
            val item = NaverFixtures.createSearchItem(
                code = "005930",
                name = "삼성전자",
                typeCode = "KOSPI",
                nationCode = "KOR"
            )

            // When
            val ticker = item.toTicker()

            // Then
            assertThat(ticker.symbol).isEqualTo("005930")
            assertThat(ticker.marketType).isEqualTo(MarketType.KOSPI)
            assertThat(ticker.nationCode).isEqualTo("KOR")
        }

        @Test
        fun `코인 검색 결과는 거래소 타입을 marketType으로 변환한다`() {
            val item = NaverFixtures.createSearchItem(
                code = "BTC",
                name = "비트코인",
                typeCode = "UPBIT",
                reutersCode = "BTC_KRW_UPBIT",
                nationCode = null,
                nationName = null,
                category = "coin"
            )

            val ticker = item.toTicker()

            assertThat(ticker.symbol).isEqualTo("BTC")
            assertThat(ticker.tradingSymbol).isEqualTo("BTC_KRW_UPBIT")
            assertThat(ticker.marketType).isEqualTo(MarketType.UPBIT)
        }

        @Test
        fun `NaverStockPrice 결과에서 TickerPrice 도메인 변환 검증 (나스닥 케이스)`() {
            // Given
            val naverStock = NaverFixtures.PRICE_TESLA_FALLING

            // When
            val tickerPrice = naverStock.toTickerPrice()

            // Then
            assertThat(tickerPrice.symbol).isEqualTo("TSLA")
            assertThat(tickerPrice.marketType).isEqualTo(MarketType.NASDAQ)
            assertThat(tickerPrice.currency.code).isEqualTo("USD")
            assertThat(tickerPrice.currentPrice).isEqualTo(180.50)
        }

        @Test
        fun `NaverStockPrice 결과에서 아시아 해외시장도 정확히 변환한다`() {
            val naverStock = NaverFixtures.createStockPrice(
                itemCode = "9988",
                stockName = "알리바바",
                reutersCode = "9988.HK",
                closePrice = "110.20",
                fluctuationsRatio = "3.50",
                stockExchangeType = NaverFixtures.createStockExchangeType(
                    code = "HKG",
                    nameEng = "HONG KONG",
                    nameKor = "홍콩거래소",
                    nationCode = "HKG",
                    nationName = "홍콩"
                ),
                currencyType = NaverFixtures.createCurrencyResponse("HKD")
            )

            val tickerPrice = naverStock.toTickerPrice()

            assertThat(tickerPrice.marketType).isEqualTo(MarketType.HONG_KONG)
            assertThat(tickerPrice.currency).isEqualTo(CurrencyType.HKD)
            assertThat(tickerPrice.currentPrice).isEqualTo(110.20)
        }

        @Test
        fun `NaverStockPrice 결과에서 도쿄 거래소도 정확히 변환한다`() {
            val naverStock = NaverFixtures.createStockPrice(
                itemCode = "9984",
                stockName = "소프트뱅크",
                reutersCode = "9984.T",
                closePrice = "9,200.00",
                fluctuationsRatio = "-1.20",
                stockExchangeType = NaverFixtures.createStockExchangeType(
                    code = "TYO",
                    nameEng = "TOKYO",
                    nameKor = "도쿄증권거래소",
                    nationCode = "JPN",
                    nationName = "일본"
                ),
                currencyType = NaverFixtures.createCurrencyResponse("JPY")
            )

            val tickerPrice = naverStock.toTickerPrice()

            assertThat(tickerPrice.marketType).isEqualTo(MarketType.TOKYO)
            assertThat(tickerPrice.currency).isEqualTo(CurrencyType.JPY)
            assertThat(tickerPrice.currentPrice).isEqualTo(9200.00)
        }

        @Test
        fun `국내 실시간 시세 응답의 NXT와 통합 가격 정보를 도메인에 보존한다`() {
            val response: NaverRealTimeStockPriceResponse = objectMapper.readValue(
                """
                {
                  "datas": [
                    {
                      "symbolCode": "005930",
                      "stockName": "삼성전자",
                      "stockExchangeType": {
                        "code": "KS",
                        "zoneId": "Asia/Seoul",
                        "nationType": "KOR",
                        "delayTime": 0,
                        "startTime": "0900",
                        "endTime": "1530",
                        "closePriceSendTime": "1630",
                        "nameKor": "코스피",
                        "nameEng": "KOSPI",
                        "nationCode": "KOR",
                        "nationName": "대한민국",
                        "stockType": "domestic",
                        "name": "KOSPI"
                      },
                      "openPrice": "310,000",
                      "highPrice": "312,000",
                      "lowPrice": "300,000",
                      "closePrice": "306,500",
                      "fluctuationsRatio": "3.72",
                      "compareToPreviousClosePrice": "11,000",
                      "accumulatedTradingVolume": "8,936,765",
                      "accumulatedTradingValue": "2,735,830백만",
                      "marketStatus": "OPEN",
                      "currencyType": { "code": "KRW", "text": "Republic of Korea won", "name": "KRW" },
                      "overMarketPriceInfo": {
                        "overMarketStatus": "OPEN",
                        "overPrice": "306,500",
                        "openPrice": "310,000",
                        "highPrice": "315,000",
                        "lowPrice": "300,000",
                        "compareToPreviousClosePrice": "11,000",
                        "fluctuationsRatio": "3.72",
                        "accumulatedTradingVolume": "9,303,125",
                        "accumulatedTradingValue": "2,875,859백만"
                      },
                      "integratedPriceInfo": {
                        "openPrice": "310,000",
                        "highPrice": "315,000",
                        "lowPrice": "300,000",
                        "accumulatedTradingVolume": "18,239,890",
                        "accumulatedTradingValue": "5,611,689백만"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )

            val tickerPrice = response.datas.first().toTickerPrice()

            assertThat(tickerPrice.currentPrice).isEqualTo(306500.0)
            assertThat(tickerPrice.overMarketPrice?.currentPrice).isEqualTo(306500.0)
            assertThat(tickerPrice.overMarketPrice?.highPrice).isEqualTo(315000.0)
            assertThat(tickerPrice.integratedPrice?.tradeVolume).isEqualTo(18239890L)
        }

        @Test
        fun `NXT가 활성 상태면 레거시 가격 변환도 NXT 세션을 선택한다`() {
            val item = NaverDomesticV2StockItem(
                itemCode = "005930",
                itemName = "삼성전자",
                marketCode = "KOSPI",
                krx = NaverDomesticV2Session(currentPrice = "100", marketState = "CLOSED"),
                nxt = NaverDomesticV2Session(currentPrice = "110", marketState = "OPEN")
            )

            assertThat(item.toLegacyPrice()?.closePrice).isEqualTo("110")
        }

        @Test
        fun `JSON 문자열이 DTO로 정상적으로 역직렬화 된다`() {
            // Given
            val json = NaverFixtures.JSON_SEARCH_SUCCESS_SAMSUNG

            // When
            val response: NaverSearchResponse = objectMapper.readValue(json)

            // Then
            assertThat(response.isSuccess).isTrue()
            assertThat(response.result?.items).hasSize(1)
            assertThat(response.result?.items?.get(0)?.name).isEqualTo("삼성전자")
        }

        @Test
        fun `지표 등락률이 없으면 등락폭으로 퍼센트를 백업 계산한다`() {
            val item = com.github.myeoungdev.marketticker.infrastructure.naver.dto.NaverMarketIndicatorItem(
                itemCode = "TEST",
                stockName = "테스트 지표",
                closePrice = "90.00",
                fluctuationsRatio = "",
                compareToPreviousClosePrice = null,
                fluctuations = "-10.00",
                marketStatus = "OPEN"
            )

            val indicator = item.toMarketIndicator(IndicatorCategory.ENERGY)
            assertThat(indicator.changeRate).isEqualTo(-10.0)
        }

        @Test
        fun `환율 항목은 시장 지표 도메인으로 변환한다`() {
            val item = NaverExchangeRateItem(
                marketIndexCd = "FX_USDKRW",
                name = "미국 USD",
                fullName = null,
                symbol = "USD",
                saleBaseRate = "1,477.60",
                changeRate = "-0.30",
                changeVal = "-4.40",
                marketStatus = "OPEN"
            )

            val indicator = item.toMarketIndicator()

            assertThat(indicator.code).isEqualTo("FX_USDKRW")
            assertThat(indicator.name).isEqualTo("USD")
            assertThat(indicator.currentPrice).isEqualTo(1477.60)
            assertThat(indicator.changeRate).isEqualTo(-0.30)
            assertThat(indicator.category).isEqualTo(IndicatorCategory.EXCHANGE_RATE)
            assertThat(indicator.unit).isEqualTo("KRW")
        }

        @Test
        fun `리서치 최신 응답은 카테고리 맵으로 역직렬화된다`() {
            val response: NaverResearchLatestResponse =
                objectMapper.readValue(NaverFixtures.JSON_RESEARCH_CATEGORY_LATEST_SUCCESS)

            assertThat(response.categoryMap()[ResearchCategoryKey.MARKET]).hasSize(1)
            assertThat(response.categoryMap()[ResearchCategoryKey.COMPANY]?.first()?.itemName).isEqualTo("RFHIC")
        }

        @Test
        fun `커뮤니티 랭킹 응답은 HOT 종목 리스트로 역직렬화된다`() {
            val response: NaverDiscussionRankingResponse =
                objectMapper.readValue(NaverFixtures.JSON_DISCUSSION_RANKING_SUCCESS)

            assertThat(response.contents).hasSize(2)
            assertThat(response.contents.first().stockPrices?.stockName).isEqualTo("삼성전자")
            assertThat(response.contents.first().toArticle().title).contains("1위")
        }

        @Test
        fun `리서치 랭킹 응답은 종목 랭킹과 최신 리포트로 역직렬화된다`() {
            val response: NaverResearchRankingResponse =
                objectMapper.readValue(NaverFixtures.JSON_RESEARCH_RANKING_SEARCH_TOP_SUCCESS)

            assertThat(response.ranking).hasSize(2)
            assertThat(response.ranking.first().itemName).isEqualTo("삼성전자")
            assertThat(response.latestResearch.first().itemCode).isEqualTo("005930")
        }
    }

    @Nested
    @DisplayName("2. Client 동작 및 네트워크 시나리오")
    inner class ClientScenarioTest {

        @Test
        fun `검색 API 정상 응답 시 NaverSearchItem 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/search.*"))
                    .withQueryParam("query", containing("삼성"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_SEARCH_SUCCESS_SAMSUNG)
                    )
            )

            // When
            val result = naverClient.searchStocks("삼성")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("삼성전자")
        }

        @Test
        fun `검색 API 결과가 없으면 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/search.*"))
                    .withQueryParam("query", containing("삼성"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_SEARCH_EMPTY)
                    )
            )

            // When
            val result = naverClient.searchStocks("존재하지않는종목")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `시세 조회 API 국내 시세 요청 성공 시 데이터를 파싱하여 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/stock.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_DOMESTIC_SUCCESS)
                    )
            )

            wireMockServer.stubFor(
                get(urlPathMatching("/worldstock/stock.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_EMPTY_SUCCESS)
                    )
            )

            // When
            val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS))

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].itemCode).isEqualTo("005930")
            assertThat(result[0].closePrice).isEqualTo("103,400")
        }

        @Test
        fun `최신 국내 v2 시세 계약을 기존 가격 DTO로 매핑한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/domestic/v2/stock"))
                    .withQueryParam("itemCodes", equalTo("005930"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                            """{"pollingInterval":7000,"datas":[{"itemCode":"005930","itemName":"삼성전자","marketCode":"KOSPI","nationCode":"KOR","krx":{"currentPrice":"103400","changePrice":"2600","changeRate":"2.58","openingPrice":"101000","highPrice":"104000","lowPrice":"100500","tradingVolume":"10079219","tradingValue":"1032919000000","marketState":"OPEN"},"currencyCode":"KRW","isinCode":"KR7005930003"}]}"""
                        )
                    )
            )

            val client = NaverClient(
                client = HttpClient.newBuilder().build(),
                domesticPriceUrl = "${wireMockServer.baseUrl()}/domestic/v2/stock"
            )

            val result = client.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS))

            assertThat(result).hasSize(1)
            assertThat(result.first().itemCode).isEqualTo("005930")
            assertThat(result.first().closePrice).isEqualTo("103400")
            assertThat(result.first().stockExchangeType.code).isEqualTo("KS")
        }

        @Test
        fun `코인 시세 조회 API 성공 시 코인 데이터를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/coin/price.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_PRICE_COIN_SUCCESS)
                    )
            )

            val result = naverClient.fetchCoinPrice(
                listOf(
                    Ticker(
                        symbol = "BTC",
                        tradingSymbol = "BTC_KRW_UPBIT",
                        name = "비트코인",
                        marketType = MarketType.UPBIT,
                        nationCode = null,
                        nationName = null
                    )
                )
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].fqnfTicker).isEqualTo("BTC_KRW_UPBIT")
            assertThat(result[0].tradePrice).isGreaterThan(0.0)
        }

        @Test
        fun `리서치 aggregate API 성공 시 카테고리 섹션을 반환한다`() {
            wireMockServer.stubFor(
                post(urlEqualTo("/research/aggregate"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_AGGREGATE_SUCCESS)
                    )
            )

            val result = naverClient.fetchResearchAggregate()

            assertThat(result.researchCategory).hasSize(2)
            assertThat(result.researchCategory.first().report.first().title).isEqualTo("3/10 KB 리서치 모닝코멘트")
        }

        @Test
        fun `리서치 인기 API 성공 시 보고서 리스트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/research/recent-popular"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_RECENT_POPULAR_SUCCESS)
                    )
            )

            val result = naverClient.fetchRecentPopularResearch()

            assertThat(result).hasSize(2)
            assertThat(result.first().title).contains("텐베거")
        }

        @Test
        fun `리서치 최신 API 성공 시 카테고리별 보고서를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/latestResearch"))
                    .withQueryParam("size", equalTo("10"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_CATEGORY_LATEST_SUCCESS)
                    )
            )

            val result = naverClient.fetchCategoryLatestResearch()

            assertThat(result.market).hasSize(1)
            assertThat(result.company.first().goalPrice).isEqualTo("100000")
        }

        @Test
        fun `리서치 산업별 API 성공 시 산업 키별 보고서를 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/research/industry-research"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_INDUSTRY_SUCCESS)
                    )
            )

            val result = naverClient.fetchIndustryResearch()

            assertThat(result.keys).contains("자동차", "게임")
            assertThat(result["자동차"].orEmpty().first().title).contains("Auto Sales")
        }

        @Test
        fun `커뮤니티 HOT API 성공 시 랭킹 응답을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/community/discussion/rankings"))
                    .withQueryParam("nationType", equalTo("KOR"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("size", equalTo("20"))
                    .withQueryParam("postType", equalTo("HOT"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_DISCUSSION_RANKING_SUCCESS)
                    )
            )

            val result = naverClient.fetchDiscussionRankings()

            assertThat(result.contents).hasSize(2)
            assertThat(result.contents.first().score).isEqualTo(604)
            assertThat(result.contents.first().toArticle().itemName).isEqualTo("삼성전자")
        }

        @Test
        fun `리서치 검색상위 API 성공 시 선택 종목 리포트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/ranking"))
                    .withQueryParam("rankingType", equalTo(ResearchRankingType.SEARCH_TOP.code))
                    .withQueryParam("selectedRank", equalTo("1"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_RANKING_SEARCH_TOP_SUCCESS)
                    )
            )

            val result = naverClient.fetchResearchRanking(ResearchRankingType.SEARCH_TOP, 1)

            assertThat(result.ranking.first().itemCode).isEqualTo("005930")
            assertThat(result.latestResearch.first().title).contains("주가 매력도")
        }

        @Test
        fun `리서치 거래대금상위 API 성공 시 선택 종목 리포트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/ranking"))
                    .withQueryParam("rankingType", equalTo(ResearchRankingType.PRICE_TOP.code))
                    .withQueryParam("selectedRank", equalTo("5"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_RANKING_PRICE_TOP_SUCCESS)
                    )
            )

            val result = naverClient.fetchResearchRanking(ResearchRankingType.PRICE_TOP, 5)

            assertThat(result.ranking.first().itemName).isEqualTo("한화시스템")
            assertThat(result.latestResearch.first().goalPrice).isEqualTo("190000")
        }

        @Test
        fun `리서치 상승 API 성공 시 선택 종목 리포트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/ranking"))
                    .withQueryParam("rankingType", equalTo(ResearchRankingType.UP.code))
                    .withQueryParam("selectedRank", equalTo("1"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_RANKING_UP_SUCCESS)
                    )
            )

            val result = naverClient.fetchResearchRanking(ResearchRankingType.UP, 1)

            assertThat(result.ranking).isNotEmpty
            assertThat(result.latestResearch).isNotEmpty
        }

        @Test
        fun `리서치 하락 API 성공 시 선택 종목 리포트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/ranking"))
                    .withQueryParam("rankingType", equalTo(ResearchRankingType.DOWN.code))
                    .withQueryParam("selectedRank", equalTo("1"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_RESEARCH_RANKING_DOWN_SUCCESS)
                    )
            )

            val result = naverClient.fetchResearchRanking(ResearchRankingType.DOWN, 1)

            assertThat(result.ranking).isNotEmpty
            assertThat(result.latestResearch).isNotEmpty
        }

        @Test
        fun `종목 리서치 API 성공 시 특정 종목 보고서 리스트를 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/research/company/by-items"))
                    .withQueryParam("itemCodes", equalTo("005930"))
                    .withQueryParam("size", equalTo("10"))
                    .withQueryParam("index", equalTo("0"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"005930":${NaverFixtures.JSON_STOCK_RESEARCH_SUCCESS}}""")
                    )
            )

            val result = naverClient.fetchStockResearch("005930")

            assertThat(result).hasSize(2)
            assertThat(result.first().itemCode).isEqualTo("005930")
            assertThat(result.first().endUrl).contains(".pdf")
            assertThat(result.first().goalPrice).isEqualTo("275000")
        }

        @Test
        fun `코인 차트 API 성공 시 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/cryptoChartData.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_COIN_SUCCESS)
                    )
            )

            val result = naverClient.fetchCryptoChartCandles(
                exchangeType = "UPBIT",
                nfTicker = "BTC",
                marketType = "KRW",
                from = LocalDateTime.parse("2026-03-03T00:00:00")
            )

            assertThat(result).isNotEmpty
            assertThat(result.first().candleId).contains("BTC_KRW_UPBIT")
        }

        @Test
        fun `코인 상세 API 성공 시 오늘 시세 정보를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/api/coin/price/UPBIT/BTC"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_COIN_OVERVIEW_SUCCESS)
                    )
            )

            val result = naverClient.fetchCoinOverview("UPBIT", "BTC")

            assertThat(result).isNotNull
            assertThat(result?.tradePrice).isEqualTo(102131000.0)
            assertThat(result?.krwPremiumRate).isEqualTo(-0.77)
            assertThat(result?.totalInfos).isNotEmpty
            assertThat(result?.profileInfo?.contentKr).contains("블록체인")
        }

        @Test
        fun `국내 지수 API 성공 시 지수 데이터를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/index.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_DOMESTIC_INDEX_SUCCESS)
                    )
            )

            val result = naverClient.fetchDomesticIndices(listOf("KOSPI", "KOSDAQ"))

            assertThat(result.datas).hasSize(1)
            assertThat(result.datas.first().itemCode).isEqualTo("KOSPI")
            assertThat(result.datas.first().closePrice).isEqualTo("5,791.91")
        }

        @Test
        fun `원자재 API 성공 시 금속 지표를 파싱한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/marketindex/metals/GCcv1.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_MARKET_METAL_SUCCESS)
                    )
            )

            val result = naverClient.fetchMarketCommodity("metals", "GCcv1")

            assertThat(result.datas).hasSize(1)
            assertThat(result.datas.first().reutersCode).isEqualTo("GCcv1")
            assertThat(result.datas.first().name).isEqualTo("국제 금")
        }

        @Test
        fun `환율 API 성공 시 환율 목록을 파싱한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/domestic/exchange/List"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_EXCHANGE_RATE_SUCCESS)
                    )
            )

            val result = naverClient.fetchExchangeRates()

            assertThat(result).hasSize(5)
            assertThat(result.first().marketIndexCd).isEqualTo("FX_USDKRW")
            assertThat(result.first().saleBaseRate).isEqualTo("1,477.60")
        }

        @Test
        fun `최신 환율 계약은 currencies query와 currencyInfo를 매핑한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/stockDomestic/exchangeRates/list"))
                    .withQueryParam("currencies", equalTo("USD,JPY,EUR,CNY,HKD"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                            """[{"currencyInfo":{"currencyCode":"USD","currencyKoreanName":"달러"},"saleBaseRate":1358.6,"changeVal":-16.4,"changeRate":-1.19}]"""
                        )
                    )
            )

            val client = NaverClient(
                client = HttpClient.newBuilder().build(),
                exchangeRateUrl = "${wireMockServer.baseUrl()}/stockDomestic/exchangeRates/list"
            )

            val result = client.fetchExchangeRates()

            assertThat(result).hasSize(1)
            assertThat(result.first().currencyInfo?.currencyCode).isEqualTo("USD")
            assertThat(result.first().saleBaseRate).isEqualTo("1358.6")
        }

        @Test
        fun `환율 API 실패 시 빈 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/domestic/exchange/List"))
                    .willReturn(
                        aResponse()
                            .withStatus(503)
                    )
            )

            val result = naverClient.fetchExchangeRates()

            assertThat(result).isEmpty()
        }

        @Test
        fun `국내 주식 차트 API 성공 시 day 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/domestic/item/005930/day.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_DOMESTIC_DAY_SUCCESS)
                    )
            )

            val result = naverClient.fetchStockChartCandles(
                ticker = TickerFixtures.SAMSUNG_ELECTRONICS,
                period = PriceHistoryService.Period.DAY
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().localDate).isEqualTo("20260305")
            assertThat(result.first().closePrice).isEqualTo(9230.0)
        }

        @Test
        fun `해외 주식 차트 API 성공 시 year 캔들 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/chart/foreign/item/AAPL.O/year.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_CHART_FOREIGN_YEAR_SUCCESS)
                    )
            )

            val result = naverClient.fetchStockChartCandles(
                ticker = TickerFixtures.APPLE,
                period = PriceHistoryService.Period.YEAR
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().localDate).isEqualTo("20100101")
            assertThat(result.first().closePrice).isEqualTo(0.385)
        }

        @Test
        fun `뉴스 리스트 API 성공 시 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("FLASHNEWS"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("pageSize", equalTo("15"))
                    .withQueryParam("date", equalTo("20260914"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS)
                    )
            )

            val result = naverClient.fetchNewsList(
                category = "FLASHNEWS",
                date = LocalDate.of(2026, 9, 14)
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().title).isEqualTo("테스트 뉴스 제목")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/003/0013805290")
        }

        @Test
        fun `뉴스 포커스 머니스토리 공지 API는 최신 웹 계약을 사용한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/focus"))
                    .withQueryParam("sid", equalTo("401"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("pageSize", equalTo("5"))
                    .withQueryParam("date", equalTo("20260914"))
                    .withQueryParam("enableFallback", equalTo("true"))
                    .withQueryParam("maxDays", equalTo("7"))
                    .willReturn(okJson("""{"articles":[{"officeID":"001","officeHName":"테스트","articleID":"1","title":"포커스","date":"20260914110000","url":"https://example.com/focus"}]}"""))
            )
            wireMockServer.stubFor(
                get(urlPathEqualTo("/content/moneyStory"))
                    .withQueryParam("mainCategoryIdList", equalTo("1"))
                    .withQueryParam("size", equalTo("20"))
                    .willReturn(okJson("""{"totalCount":1,"moneyContentList":[{"title":"머니","displayAt":"2026-09-14T11:00:00","imageUrl":"https://example.com/money.jpg"}]}"""))
            )
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/noticeList"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("pageSize", equalTo("2"))
                    .withQueryParam("keyword", equalTo("공시"))
                    .withQueryParam("startDate", equalTo("20260901"))
                    .withQueryParam("endDate", equalTo("20260914"))
                    .withQueryParam("typeIdx", equalTo("ST"))
                    .withQueryParam("enableFallback", equalTo("true"))
                    .withQueryParam("maxDays", equalTo("3"))
                    .willReturn(okJson("""{"content":[{"no":"1","title":"공지","datetime":"2026-09-14T11:00:00"}],"totalPages":1,"totalElements":1,"last":true,"number":0,"size":2}"""))
            )

            val focus = naverClient.fetchNewsFocusResult(401, date = LocalDate.of(2026, 9, 14))
            val money = naverClient.fetchMoneyStory(size = 20)
            val notice = naverClient.fetchNoticePage(
                pageSize = 2,
                keyword = "공시",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2026, 9, 14),
                typeIdx = listOf("ST")
            )

            assertThat(focus.status).isEqualTo(NaverFetchStatus.SUCCESS)
            assertThat(focus.value.articles).hasSize(1)
            assertThat(focus.value.articles.first().toNewsArticle("시장").articleUrl()).isEqualTo("https://example.com/focus")
            assertThat(money.status).isEqualTo(NaverFetchStatus.SUCCESS)
            assertThat(money.value.moneyContentList).hasSize(1)
            assertThat(notice.status).isEqualTo(NaverFetchStatus.SUCCESS)
            assertThat(notice.value.content).hasSize(1)
            assertThat(notice.value.totalElements).isEqualTo(1)
        }

        @Test
        fun `주요 뉴스 리스트 API는 현재 페이지 계약을 사용한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("MAINNEWS"))
                    .withQueryParam("page", equalTo("2"))
                    .withQueryParam("pageSize", equalTo("20"))
                    .withQueryParam("date", equalTo("20260914"))
                    .willReturn(okJson(NaverFixtures.JSON_NEWS_LIST_FLASH_SUCCESS))
            )

            val result = naverClient.fetchNewsList(
                category = "MAINNEWS",
                page = 2,
                pageSize = 20,
                date = LocalDate.of(2026, 9, 14)
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().title).isEqualTo("테스트 뉴스 제목")
        }

        @Test
        fun `랭킹 뉴스 API 성공 시 랭킹 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("RANKNEWS"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("pageSize", equalTo("15"))
                    .withQueryParam("date", equalTo("20260914"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_NEWS_LIST_RANK_SUCCESS)
                    )
            )

            val result = naverClient.fetchNewsList(
                category = "RANKNEWS",
                date = LocalDate.of(2026, 9, 14)
            )

            assertThat(result).hasSize(1)
            assertThat(result.first().title).contains("외국인")
            assertThat(result.first().ranking).isEqualTo("1")
            assertThat(result.first().sumCount).isEqualTo("29885")
            assertThat(result.first().subcontent).contains("요약")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/015/0005260111")
        }

        @Test
        fun `해외 뉴스 API 성공 시 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/foreign/news/worldNews"))
                    .withQueryParam("page", equalTo("1"))
                    .withQueryParam("pageSize", equalTo("15"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_WORLD_NEWS_SUCCESS)
                    )
            )

            val result = naverClient.fetchWorldNews(page = 1, pageSize = 15)

            assertThat(result).hasSize(1)
            assertThat(result.first().title).contains("우크라이나")
            assertThat(result.first().officeHname).isEqualTo("로이터")
            assertThat(result.first().articleUrl()).isEqualTo("https://stock.naver.com/news/worldnews/2509419")
            assertThat(result.first().subcontent).contains("전력 수입")
        }

        @Test
        fun `뉴스 리스트 API 실패와 빈 응답을 상태로 구분한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("MAINNEWS"))
                    .willReturn(serverError())
            )
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("FLASHNEWS"))
                    .willReturn(okJson("""{"articles":[]}"""))
            )

            val failed = naverClient.fetchNewsListResult(category = "MAINNEWS")
            val empty = naverClient.fetchNewsListResult(category = "FLASHNEWS")

            assertThat(failed.status).isEqualTo(NewsLoadStatus.FAILED)
            assertThat(empty.status).isEqualTo(NewsLoadStatus.EMPTY)
        }

        @Test
        fun `랭킹 뉴스는 집계 홈이 아닌 리스트 API를 사용한다`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/list"))
                    .withQueryParam("category", equalTo("RANKNEWS"))
                    .willReturn(okJson(NaverFixtures.JSON_NEWS_LIST_RANK_SUCCESS))
            )
            wireMockServer.stubFor(
                get(urlPathEqualTo("/news/aggregate/home"))
                    .willReturn(notFound())
            )

            val result = naverClient.fetchRankingNews(limit = 15)

            assertThat(result).hasSize(1)
            wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/news/list")))
            wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/news/aggregate/home")))
        }

        @Test
        fun `종목 상세 뉴스 API 성공 시 클러스터를 평탄화한 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/detail/news.*"))
                    .withQueryParam("itemCode", equalTo("NVDA.O"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_DOMESTIC_DETAIL_NEWS_SUCCESS)
                    )
            )

            val result = naverClient.fetchDomesticDetailNews(itemCode = "NVDA.O", page = 1, pageSize = 15)

            assertThat(result).hasSize(3)
            assertThat(result.first().title).contains("리사 수 AMD")
            assertThat(result.first().officeHname).isEqualTo("서울경제")
            assertThat(result.first().subcontent).contains("AI 공급망")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/011/0004598294")
        }

        @Test
        fun `국내 종목 상세 API 기본 조회는 codeType 없이 핵심 지표와 기업 설명을 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/domestic/detail/003280/detail"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_DOMESTIC_STOCK_DETAIL_SUCCESS)
                    )
            )

            val result = naverClient.fetchDomesticStockDetail("003280")

            assertThat(result).isNotNull
            assertThat(result?.itemname).isEqualTo("흥아해운")
            assertThat(result?.per).isEqualTo("20.48")
            assertThat(result?.pbr).isEqualTo("2.46")
            assertThat(result?.summaryText()).contains("해운기업", "종합물류기업")
        }

        @Test
        fun `국내 종목 상세 API는 NXT codeType으로 조회할 수 있다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/domestic/detail/005930/detail?codeType=NXT"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                  "itemcode": "005930",
                                  "itemname": "삼성전자",
                                  "nowPrice": "306500",
                                  "prevChangeRate": "3.72",
                                  "per": "24.75",
                                  "pbr": "4.26",
                                  "comment1": "NXT 기준 상세 정보입니다."
                                }
                                """.trimIndent()
                            )
                    )
            )

            val result = naverClient.fetchDomesticStockDetail("005930", DomesticTradeType.NXT)

            assertThat(result).isNotNull
            assertThat(result?.itemname).isEqualTo("삼성전자")
            assertThat(result?.nowVal).isEqualTo("306500")
            assertThat(result?.changeRate).isEqualTo("3.72")
        }

        @Test
        fun `종목 상세 뉴스 API는 itemCode가 비어 있으면 빈 목록을 반환한다`() {
            val result = naverClient.fetchDomesticDetailNews(itemCode = "   ", page = 1, pageSize = 15)

            assertThat(result).isEmpty()
            wireMockServer.verify(0, anyRequestedFor(anyUrl()))
        }

        @Test
        fun `종목 상세 뉴스 API 실패 시 빈 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/detail/news.*"))
                    .withQueryParam("itemCode", equalTo("NVDA.O"))
                    .willReturn(aResponse().withStatus(500))
            )

            val result = naverClient.fetchDomesticDetailNews(itemCode = "NVDA.O", page = 1, pageSize = 15)

            assertThat(result).isEmpty()
        }

        @Test
        fun `해외 종목 뉴스 API 성공 시 기사 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/foreign/worldStock/list.*"))
                    .withQueryParam("reutersCode", equalTo("NVDA.O"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_FOREIGN_STOCK_NEWS_SUCCESS)
                    )
            )

            val result = naverClient.fetchForeignStockNews(reutersCode = "NVDA.O", page = 1, pageSize = 15)

            assertThat(result).hasSize(2)
            assertThat(result.first().title).contains("신틸 포토닉스")
            assertThat(result.first().officeHname).isEqualTo("로이터")
            assertThat(result.first().badgeLabel).isEqualTo("해외 종목뉴스")
            assertThat(result.first().articleUrl()).isEqualTo("https://stock.naver.com/news/worldnews/2512970")
        }

        @Test
        fun `해외 종목 뉴스 API는 reutersCode가 비어 있으면 빈 목록을 반환한다`() {
            val result = naverClient.fetchForeignStockNews(reutersCode = " ", page = 1, pageSize = 15)

            assertThat(result).isEmpty()
            wireMockServer.verify(0, anyRequestedFor(anyUrl()))
        }

        @Test
        fun `해외 종목 뉴스 API 실패 시 빈 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/foreign/worldStock/list.*"))
                    .withQueryParam("reutersCode", equalTo("NVDA.O"))
                    .willReturn(aResponse().withStatus(502))
            )

            val result = naverClient.fetchForeignStockNews(reutersCode = "NVDA.O", page = 1, pageSize = 15)

            assertThat(result).isEmpty()
        }

        @Test
        fun `해외 종목 개요 API 성공 시 회사 개요를 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/securityService/stock/NVDA.O/overview"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_FOREIGN_STOCK_OVERVIEW_SUCCESS)
                    )
            )

            val result = naverClient.fetchForeignStockOverview("NVDA.O")

            assertThat(result).isNotNull
            assertThat(result?.companyName).isEqualTo("엔비디아")
            assertThat(result?.industry?.industryGroupKor).isEqualTo("반도체")
            assertThat(result?.summaries?.representativeName).isEqualTo("Jen-Hsun Huang")
            assertThat(result?.stockItemListedInfo?.marketValueKrw).isEqualTo("6,678조 2,551억원")
        }

        @Test
        fun `해외 종목 개요 API는 reutersCode가 비어 있으면 null을 반환한다`() {
            val result = naverClient.fetchForeignStockOverview("   ")

            assertThat(result).isNull()
            wireMockServer.verify(0, anyRequestedFor(anyUrl()))
        }

        @Test
        fun `해외 종목 개요 API 실패 시 null을 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/securityService/stock/NVDA.O/overview"))
                    .willReturn(aResponse().withStatus(503))
            )

            val result = naverClient.fetchForeignStockOverview("NVDA.O")

            assertThat(result).isNull()
        }

        @Test
        fun `해외 종목 기본 API 성공 시 핵심 지표를 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/securityService/stock/NVDA.O/basic"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_FOREIGN_STOCK_BASIC_SUCCESS)
                    )
            )

            val result = naverClient.fetchForeignStockBasic("NVDA.O")

            assertThat(result).isNotNull
            assertThat(result?.stockName).isEqualTo("엔비디아")
            assertThat(result?.closePrice).isEqualTo("185.62")
            assertThat(result?.fluctuationsRatio).isEqualTo("0.47")
            assertThat(result?.stockItemTotalInfos?.first { it.code == "per" }?.value).isEqualTo("37.73배")
            assertThat(result?.stockItemTotalInfos?.first { it.code == "highPriceOf52Weeks" }?.value).isEqualTo("212.19")
        }

        @Test
        fun `해외 종목 기본 API는 reutersCode가 비어 있으면 null을 반환한다`() {
            val result = naverClient.fetchForeignStockBasic(" ")

            assertThat(result).isNull()
            wireMockServer.verify(0, anyRequestedFor(anyUrl()))
        }

        @Test
        fun `해외 종목 기본 API 실패 시 null을 반환한다`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/securityService/stock/NVDA.O/basic"))
                    .willReturn(aResponse().withStatus(500))
            )

            val result = naverClient.fetchForeignStockBasic("NVDA.O")

            assertThat(result).isNull()
        }

        @Test
        fun `뉴스 검색 API 성공 시 코인 뉴스 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/news/search.*"))
                    .withQueryParam("query", containing("BTC"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(NaverFixtures.JSON_NEWS_SEARCH_CRYPTO_SUCCESS)
                    )
            )

            val result = naverClient.fetchNewsSearch("비트코인 | BTC | Bitcoin", page = 1, pageSize = 7)

            assertThat(result).hasSize(2)
            assertThat(result.first().title).contains("코인시장")
            assertThat(result.first().officeHname).isEqualTo("비즈워치")
            assertThat(result.first().badgeLabel).isEqualTo("코인뉴스")
            assertThat(result.first().articleUrl()).isEqualTo("https://n.news.naver.com/article/648/0000045277")
        }

        @Test
        fun `뉴스 검색 API는 query가 비어 있으면 빈 목록을 반환한다`() {
            val result = naverClient.fetchNewsSearch("   ", page = 1, pageSize = 7)

            assertThat(result).isEmpty()
            wireMockServer.verify(0, anyRequestedFor(anyUrl()))
        }

        @Test
        fun `공지 리스트 API 성공 시 공지 요약 목록을 반환한다`() {
            wireMockServer.stubFor(
                get(urlPathMatching("/news/noticeList.*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"content":${NaverFixtures.JSON_NOTICE_LIST_SUCCESS}}""")
                    )
            )

            val result = naverClient.fetchNoticeList(page = 1, pageSize = 5)

            assertThat(result).hasSize(2)
            assertThat(result.first().title).contains("서머타임")
            assertThat(result.first().category).isEqualTo("거래시간")
        }
    }

    @Test
    fun `시세 조회 API 국내, 해외 시세 요청 성공 시 결과를 병합해서 반환한다`() {
        // Given
        wireMockServer.stubFor(
            get(urlPathMatching("/domestic/stock.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NaverFixtures.JSON_PRICE_DOMESTIC_SUCCESS)
                )
        )

        wireMockServer.stubFor(
            get(urlPathMatching("/worldstock/stock.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NaverFixtures.JSON_PRICE_WORLD_SUCCESS)
                )
        )

        // When
        val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS, TickerFixtures.APPLE))

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].itemCode).isEqualTo("005930")
        assertThat(result[0].stockName).isEqualTo("삼성전자")
        assertThat(result[1].itemCode).isEqualTo("AAPL")
        assertThat(result[1].stockName).isEqualTo("애플")

    }

    @Nested
    @DisplayName("3. 네트워크 장애 및 예외 처리")
    inner class ErrorHandlingTest {

        @Test
        fun `검색 API HTTP 500 서버 오류 발생 시 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            // When
            val result = naverClient.searchStocks("삼성")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `시세 조회 API HTTP 404 잘못된 URL 요청 시 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(404))
            )

            // When
            val result = naverClient.fetchStockPrice(listOf(TickerFixtures.SAMSUNG_ELECTRONICS))

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `검색 API 네트워크 오류(Connection Reset) 발생 시 예외를 잡고 빈 리스트를 반환한다`() {
            // Given
            wireMockServer.stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse()
                            .withFault(Fault.CONNECTION_RESET_BY_PEER)
                    )
            )

            // When
            val result = naverClient.searchStocks("지연")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        fun `API 응답은 200이지만 isSuccess=false인 경우 빈 리스트 반환`() {
            // Given
            val errorResponse = NaverFixtures.createSearchResponse(
                isSuccess = false,
                message = "Invalid Query",
                result = null
            )

            wireMockServer.stubFor(
                get(urlPathMatching("/domestic/stock.*"))
                    .withQueryParam("query", containing("삼성전자"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(errorResponse))
                    )
            )

            // When
            val result = naverClient.searchStocks("오류")

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("4. 코인 차트 보정")
    inner class CoinChartMergeTest {

        @Test
        fun `코인 상세 응답은 마지막 일봉을 오늘 시세로 보정한다`() {
            val overview: NaverCoinOverview = objectMapper.readValue(NaverFixtures.JSON_COIN_OVERVIEW_SUCCESS)
            val chartResponse: NaverCryptoChartResponse = objectMapper.readValue(NaverFixtures.JSON_CHART_COIN_SUCCESS)

            val merged = overview.mergeDailyCandles(
                candles = chartResponse.result.map { it.toPriceHistoryCandle() },
                zoneId = ZoneId.of("Asia/Seoul")
            )

            assertThat(merged).isNotEmpty
            assertThat(merged.last().close).isEqualTo(102131000.0)
            assertThat(merged.last().high).isEqualTo(102200000.0)
            assertThat(merged.last().low).isEqualTo(97668000.0)
            assertThat(merged.last().volume).isEqualTo(2083L)
        }
    }
}
