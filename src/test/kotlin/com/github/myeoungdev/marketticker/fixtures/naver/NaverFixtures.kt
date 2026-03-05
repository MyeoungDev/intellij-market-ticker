package com.github.myeoungdev.marketticker.fixtures.naver

import com.github.myeoungdev.marketticker.infrastructure.naver.dto.*

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-04
 */
object NaverFixtures {

    /**
     * NaverSearchResponse (최상위 검색 응답 래퍼) 생성
     */
    fun createSearchResponse(
        isSuccess: Boolean = true,
        detailCode: String? = null,
        message: String? = null,
        result: SearchResultPayload? = createSearchResultPayload()
    ): NaverSearchResponse {
        return NaverSearchResponse(
            isSuccess = isSuccess,
            detailCode = detailCode,
            message = message,
            result = result
        )
    }

    /**
     * SearchResultPayload (검색 결과 컨테이너) 생성
     */
    fun createSearchResultPayload(
        query: String = "삼성",
        items: List<NaverSearchItem> = listOf(createSearchItem())
    ): SearchResultPayload {
        return SearchResultPayload(
            query = query,
            items = items
        )
    }

    /**
     * NaverSearchItem (개별 종목 정보) 생성 - 기본값: 삼성전자(KOSPI)
     */
    fun createSearchItem(
        code: String = "005930",
        name: String = "삼성전자",
        typeCode: String = "KOSPI",
        typeName: String = "코스피",
        url: String = "/domestic/stock/005930/total",
        reutersCode: String = "005930",
        nationCode: String? = "KOR",
        nationName: String? = "대한민국",
        category: String = "stock"
    ): NaverSearchItem {
        return NaverSearchItem(
            code = code,
            name = name,
            typeCode = typeCode,
            typeName = typeName,
            url = url,
            reutersCode = reutersCode,
            nationCode = nationCode,
            nationName = nationName,
            category = category
        )
    }

    /**
     * NaverRealTimeStockPriceResponse (시세 조회 응답 래퍼) 생성
     */
    fun createStockPriceResponse(
        pollingInterval: Long = 10000,
        time: String = "20260123100000",
        datas: List<NaverStockPrice> = listOf(createStockPrice())
    ): NaverRealTimeStockPriceResponse {
        return NaverRealTimeStockPriceResponse(
            pollingInterval = pollingInterval,
            time = time,
            datas = datas
        )
    }

    /**
     * NaverStockPrice (개별 종목 시세) 생성 - 기본값: 삼성전자
     */
    fun createStockPrice(
        itemCode: String = "005930",
        stockName: String = "삼성전자",
        reutersCode: String = "005930",
        closePrice: String = "70,000", // 현재가
        compareToPreviousClosePrice: String = "1,000", // 변동액
        fluctuationsRatio: String = "1.45", // 변동률
        marketStatus: String = "OPEN",
        stockExchangeType: StockExchangeType = createStockExchangeType(),
        currencyType: CurrencyResponse = createCurrencyResponse(),
        openPrice: String = "69,000",
        highPrice: String = "71,000",
        lowPrice: String = "69,000",
        accumulatedTradingVolume: String = "10,000,000",
        accumulatedTradingValue: String = "700,000",
        isinCode: String? = "KR7005930003"
    ): NaverStockPrice {
        return NaverStockPrice(
            itemCode = itemCode,
            stockName = stockName,
            reutersCode = reutersCode,
            stockExchangeType = stockExchangeType,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            closePrice = closePrice,
            fluctuationsRatio = fluctuationsRatio,
            compareToPreviousClosePrice = compareToPreviousClosePrice,
            accumulatedTradingVolume = accumulatedTradingVolume,
            accumulatedTradingValue = accumulatedTradingValue,
            marketStatus = marketStatus,
            isinCode = isinCode,
            currencyType = currencyType
        )
    }

    /**
     * StockExchangeType (거래소 정보) 생성 - 기본값: 코스피
     */
    fun createStockExchangeType(
        code: String = "KS",
        nameEng: String = "KOSPI",
        nameKor: String = "코스피",
        nationCode: String = "KR",
        nationName: String = "대한민국"
    ): StockExchangeType {
        return StockExchangeType(
            code = code,
            nameEng = nameEng,
            nameKor = "코스피",
            zoneId = "Asia/Seoul",
            nationType = "KOR",
            delayTime = 0,
            startTime = "0900",
            endTime = "1530",
            closePriceSendTime = "1630",
            nationCode = nationCode,
            nationName = nationName,
            stockType = "domestic",
            name = nameEng
        )
    }

    fun createCurrencyResponse(code: String = "KRW"): CurrencyResponse {
        return CurrencyResponse(
            code = code,
            text = "Won",
            name = "Won"
        )
    }

    /** 검색 결과 - 삼성전자 */
    val SEARCH_ITEM_SAMSUNG = createSearchItem()

    /** 검색 결과 - 애플 (NASDAQ) */
    val SEARCH_ITEM_APPLE = createSearchItem(
        code = "AAPL",
        name = "Apple Inc",
        typeCode = "NASDAQ",
        typeName = "나스닥",
        reutersCode = "AAPL.O",
        nationCode = "USA",
        nationName = "미국"
    )

    /** 시세 - 삼성전자 (상승) */
    val PRICE_SAMSUNG_RISING = createStockPrice(
        itemCode = "005930",
        closePrice = "72,000",
        fluctuationsRatio = "2.85", // +2.85%
        marketStatus = "OPEN"
    )

    /** 시세 - 테슬라 (하락, 나스닥) */
    val PRICE_TESLA_FALLING = createStockPrice(
        itemCode = "TSLA",
        stockName = "Tesla",
        reutersCode = "TSLA.O",
        closePrice = "180.50",
        fluctuationsRatio = "-5.2", // -5.2%
        stockExchangeType = createStockExchangeType("NS", "NASDAQ", "US", "미국"),
        currencyType = createCurrencyResponse("USD")
    )

    const val JSON_SEARCH_SUCCESS_SAMSUNG = """
    {
      "isSuccess": true,
      "detailCode": "",
      "message": "",
      "result": {
        "query": "삼성",
        "items": [
          {
            "code": "005930",
            "name": "삼성전자",
            "typeCode": "KOSPI",
            "typeName": "코스피",
            "url": "/domestic/stock/005930/total",
            "reutersCode": "005930",
            "nationCode": "KOR",
            "nationName": "대한민국",
            "category": "stock"
          }
        ]
      }
    }
    """

    const val JSON_SEARCH_EMPTY = """
    {
      "isSuccess": true,
      "result": {
        "query": "없는종목",
        "items": []
      }
    }
    """

    const val JSON_PRICE_COIN_SUCCESS = """
    {
      "pollingInterval": 7000,
      "datas": [
        {
          "fqnfTicker": "BTC_KRW_UPBIT",
          "openPrice": 100578000,
          "highPrice": 101549000,
          "lowPrice": 97493000,
          "tradePrice": 99886000,
          "previousClosePrice": 100520000,
          "change": "FALLING",
          "changeRate": -0.63,
          "changeValue": -634000,
          "accumulatedTradingVolume": 3131.98199876,
          "accumulatedTradingValue": 312977052870.05096,
          "koreaTradedAt": "2026-03-04T00:58:25"
        }
      ],
      "time": "20260304005830"
    }
    """

    const val JSON_CHART_COIN_SUCCESS = """
    {
      "isSuccess": true,
      "detailCode": "",
      "message": "",
      "result": [
        {
          "candleId": "BTC_KRW_UPBIT_DAY1_2026-03-03",
          "tradeBaseAt": "2026-03-03T00:00:00Z",
          "openPrice": 100578000,
          "highPrice": 101549000,
          "lowPrice": 97493000,
          "closePrice": 99878000,
          "accumulatedTradingVolume": 3020.62747538
        }
      ]
    }
    """

    const val JSON_DOMESTIC_INDEX_SUCCESS = """
    {
      "pollingInterval": 70000,
      "datas": [
        {
          "itemCode": "KOSPI",
          "stockName": "코스피",
          "closePrice": "5,791.91",
          "compareToPreviousClosePrice": "-452.22",
          "fluctuationsRatio": "-7.24",
          "openPrice": "6,165.15",
          "highPrice": "6,180.45",
          "lowPrice": "5,791.65",
          "accumulatedTradingVolume": "1,227,193천주",
          "accumulatedTradingValue": "52,800,573백만",
          "marketStatus": "CLOSE",
          "symbolCode": "KOSPI"
        }
      ],
      "time": "20260304005834"
    }
    """

    const val JSON_MARKET_METAL_SUCCESS = """
    {
      "pollingInterval": 7000,
      "datas": [
        {
          "reutersCode": "GCcv1",
          "symbolCode": "GC",
          "name": "국제 금",
          "closePrice": "5,081.20",
          "fluctuations": "-230.40",
          "fluctuationsRatio": "-4.34",
          "openPrice": "5,335.70",
          "highPrice": "5,394.20",
          "lowPrice": "5,005.00",
          "accumulatedTradingVolume": "203,802",
          "marketStatus": "OPEN",
          "unit": "USD/OZS"
        }
      ],
      "time": "20260304005832"
    }
    """

    const val JSON_PRICE_DOMESTIC_SUCCESS = """
    {
       "pollingInterval":7000,
       "datas":[
          {
             "itemCode":"005930",
             "stockName":"삼성전자",
             "stockExchangeType":{
                "code":"KS",
                "zoneId":"Asia/Seoul",
                "nationType":"KOR",
                "delayTime":0,
                "startTime":"0900",
                "endTime":"1530",
                "closePriceSendTime":"1630",
                "nameKor":"코스피",
                "nameEng":"KOSPI",
                "nationCode":"KOR",
                "nationName":"대한민국",
                "stockType":"domestic",
                "name":"KOSPI"
             },
             "closePrice":"103,400",
             "compareToPreviousClosePrice":"2,600",
             "compareToPreviousPrice":{
                "code":"2",
                "text":"상승",
                "name":"RISING"
             },
             "fluctuationsRatio":"2.58",
             "tradeStopType":{
                "code":"1",
                "text":"운영.Trading",
                "name":"TRADING"
             },
             "openPrice":"101,200",
             "highPrice":"103,500",
             "lowPrice":"101,000",
             "accumulatedTradingVolume":"10,079,219",
             "accumulatedTradingValue":"1,032,919백만",
             "marketStatus":"OPEN",
             "localTradedAt":"2025-12-02T13:48:08.504191+09:00",
             "overMarketPriceInfo":{
                "tradingSessionType":"REGULAR_MARKET",
                "overMarketStatus":"OPEN",
                "overPrice":"103,400",
                "openPrice":"101,000",
                "highPrice":"103,400",
                "lowPrice":"100,800",
                "compareToPreviousPrice":{
                   "code":"2",
                   "text":"상승",
                   "name":"RISING"
                },
                "compareToPreviousClosePrice":"2,600",
                "fluctuationsRatio":"2.58",
                "localTradedAt":"2025-12-02T13:48:08.504209+09:00",
                "tradeStopType":{
                   "code":"1",
                   "text":"운영.Trading",
                   "name":"TRADING"
                },
                "accumulatedTradingVolume":"4,763,607",
                "accumulatedTradingValue":"487,008백만"
             },
             "integratedPriceInfo":{
                "openPrice":"101,000",
                "highPrice":"103,500",
                "lowPrice":"100,800",
                "accumulatedTradingVolume":"14,842,826",
                "accumulatedTradingValue":"1,519,927백만"
             },
             "isinCode":"KR7005930003",
             "myDataCode":null,
             "stockEndUrl":null,
             "symbolCode":"005930",
             "currencyType":{
                "code":"KRW",
                "text":"Republic of Korea won",
                "name":"KRW"
             }
          }
       ]
    }
    """

    const val JSON_PRICE_WORLD_SUCCESS = """
        {
           "pollingInterval":70000,
           "datas":[
              {
                 "reutersCode":"AAPL.O",
                 "stockName":"애플",
                 "symbolCode":"AAPL",
                 "stockExchangeType":{
                    "code":"NSQ",
                    "zoneId":"EST5EDT",
                    "nationType":"USA",
                    "delayTime":0,
                    "startTime":"0930",
                    "endTime":"1600",
                    "closePriceSendTime":"2031",
                    "nameKor":"나스닥 증권거래소",
                    "nameEng":"NASDAQ Stock Exchange",
                    "nationCode":"USA",
                    "nationName":"미국",
                    "stockType":"worldstock",
                    "name":"NASDAQ"
                 },
                 "closePrice":"284.15",
                 "compareToPreviousClosePrice":"-2.04",
                 "compareToPreviousPrice":{
                    "code":"5",
                    "text":"하락",
                    "name":"FALLING"
                 },
                 "fluctuationsRatio":"-0.71",
                 "tradeStopType":{
                    "code":"1",
                    "text":"운영.Trading",
                    "name":"TRADING"
                 },
                 "openPrice":"286.20",
                 "highPrice":"288.62",
                 "lowPrice":"283.30",
                 "accumulatedTradingVolume":"43,538,687",
                 "accumulatedTradingValue":"124억 USD",
                 "localTradedAt":"2025-12-03T16:00:00-05:00",
                 "marketStatus":"CLOSE",
                 "overMarketPriceInfo":{
                    "tradingSessionType":"AFTER_MARKET",
                    "overMarketStatus":"CLOSE",
                    "overPrice":"284.22",
                    "compareToPreviousPrice":{
                       "code":"2",
                       "text":"상승",
                       "name":"RISING"
                    },
                    "compareToPreviousClosePrice":"0.07",
                    "fluctuationsRatio":"0.02",
                    "localTradedAt":"2025-12-03T20:00:00-05:00"
                 },
                 "currencyType":{
                    "code":"USD",
                    "text":"US dollar",
                    "name":"USD"
                 },
                 "isinCode":"US0378331005",
                 "myDataCode":null,
                 "stockEndUrl":null,
                 "marketValueFull":"4,198,700,704,950",
                 "marketValueHangeul":"4조 1,987억 USD",
                 "marketValueKrwHangeul":"6,187조 6,252억원"
              }
           ],
           "time":"20251204131306"
        }
    """

    const val JSON_PRICE_EMPTY_SUCCESS = """
    {
       "pollingInterval":7000,
       "datas":[]
    }
    """

}
