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
     * 코인 상세 응답 JSON 입니다.
     */
    const val JSON_COIN_OVERVIEW_SUCCESS = """
    {
      "fqnfTicker": "BTC_KRW_UPBIT",
      "nfTicker": "BTC",
      "exchangeTicker": "BTC",
      "krName": "비트코인",
      "enName": "Bitcoin",
      "exchangeType": "UPBIT",
      "exchangeName": "업비트",
      "tradePrice": 102131000,
      "change": "RISING",
      "changeRate": 4.38,
      "changeValue": 4286000,
      "krwPremiumRate": -0.77,
      "koreaTradedAt": "2026-03-09T23:41:53.384",
      "totalInfos": [
        { "code": "previousClosePrice", "key": "전일", "value": 97845000 },
        { "code": "openPrice", "key": "시가", "value": 97845000 },
        { "code": "highPrice", "key": "고가", "value": 102200000 },
        { "code": "lowPrice", "key": "저가", "value": 97668000 },
        { "code": "tradePrice", "key": "거래가", "value": 102131000 },
        { "code": "accumulatedTradingVolume", "key": "거래량", "value": 2083.76351566 },
        { "code": "lowest52weekPrice", "key": "52주 최저", "value": 89000000 },
        { "code": "highest52weekPrice", "key": "52주 최고", "value": 179869000 }
      ],
      "profileInfo": {
        "contentKr": "비트코인은 블록체인 기술을 적용해 만든 네트워크입니다.",
        "marketCap": 2055450638697925.2,
        "symbolImageUrl": "https://stock-phinf.pstatic.net/test/BTC_64.png"
      }
    }
    """

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

    const val JSON_CHART_DOMESTIC_DAY_SUCCESS = """
    [
      {
        "localDate": "20260305",
        "closePrice": 9230,
        "openPrice": 9000,
        "highPrice": 9880,
        "lowPrice": 8600,
        "accumulatedTradingVolume": 94858322,
        "foreignRetentionRate": 0.37
      }
    ]
    """

    const val JSON_CHART_FOREIGN_YEAR_SUCCESS = """
    [
      {
        "localDate": "20100101",
        "closePrice": 0.385,
        "openPrice": 0.4628,
        "highPrice": 0.474,
        "lowPrice": 0.2163,
        "accumulatedTradingVolume": 186832539320
      }
    ]
    """

    const val JSON_NEWS_LIST_FLASH_SUCCESS = """
    {
      "articles": [
        {
          "officeId": "003",
          "officeHname": "뉴시스",
          "articleId": "0013805290",
          "title": "테스트 뉴스 제목",
          "datetime": "2026-03-06 09:43:54",
          "type": "1",
          "subcontent": "테스트 본문",
          "thumbUrl": "https://imgnews.pstatic.net/test.jpg"
        }
      ],
      "date": "20260306",
      "isFirstDate": true
    }
    """

    const val JSON_NEWS_LIST_RANK_SUCCESS = """
    {
      "articles": [
        {
          "officeId": "015",
          "officeHname": "한국경제",
          "articleId": "0005260111",
          "title": "코스피 '10조' 팔더니…5일 만에 외국인 '1300억' 담은 회사 [종목+]",
          "datetime": "2026-03-09 20:01:14",
          "type": "1",
          "subcontent": "외국인 수급과 건설주 유입 배경을 다룬 기사 요약입니다.",
          "ranking": "1",
          "prevRanking": "2",
          "sumCount": "29885",
          "thumbUrl": "https://imgnews.pstatic.net/image/origin/015/2026/03/09/5260111.jpg?type=nf206_146"
        }
      ],
      "date": "20260309",
      "isFirstDate": false
    }
    """

    const val JSON_WORLD_NEWS_SUCCESS = """
    [
      {
        "oid": "fnGuide",
        "ohnm": "로이터",
        "aid": "2509419",
        "tit": "우크라이나의 전력 수입이 원자력 발전소 수리로 증가한다고 ExPro 컨설팅은 말합니다.",
        "dt": "20260310001018",
        "updatedt": "20260310001018",
        "type": "0",
        "subcontent": "우크라이나의 전력 수입 증가 배경을 다룬 해외 뉴스 요약입니다.",
        "thumbUrl": null,
        "relatedItems": []
      }
    ]
    """

    const val JSON_NEWS_SEARCH_CRYPTO_SUCCESS = """
    {
      "status": {
        "code": "0",
        "message": "OK",
        "isSuccess": true
      },
      "total": "4177",
      "items": [
        {
          "id": "6480000045277",
          "officeId": "648",
          "articleId": "0000045277",
          "officeName": "비즈워치",
          "datetime": "202603141100",
          "type": "1",
          "title": "\"유가 200달러 각오하라\"…코인시장 어디로?",
          "body": "비트코인(BTC)과 이더리움(ETH) 등 주요 코인이 박스권에 머물고 있다.",
          "photoType": "1",
          "imageOriginLink": "https://imgnews.pstatic.net/image/origin/648/2026/03/14/45277.jpg"
        },
        {
          "id": "0140005491218",
          "officeId": "014",
          "articleId": "0005491218",
          "officeName": "파이낸셜뉴스",
          "datetime": "202603131949",
          "type": "1",
          "title": "글로벌 증시 변동 속 비트코인 1억569만원…알트코인 동반 상승",
          "body": "비트코인이 1억원선 부근에서 거래되며 비교적 소폭 상승했다.",
          "photoType": "1",
          "imageOriginLink": "https://imgnews.pstatic.net/image/origin/014/2026/03/13/5491218.jpg"
        }
      ]
    }
    """

    const val JSON_DOMESTIC_DETAIL_NEWS_SUCCESS = """
    {
      "total": "52251",
      "clusters": [
        {
          "itemTotal": "2",
          "items": [
            {
              "id": "0110004598294",
              "officeId": "011",
              "articleId": "0004598294",
              "officeName": "서울경제",
              "datetime": "202603112349",
              "type": "1",
              "title": "리사 수 AMD 방한, 삼성·네이버와 회동",
              "body": "전 세계 인공지능(AI)용 그래픽처리장치 시장 2위 업체를 이끌고 있는 리사 수 AMD 최고경영자가 다음 주 한국을 방문한다. AI 공급망 협력을 강화할 것이라는 전망이 나온다.",
              "photoType": "1",
              "imageOriginLink": "https://imgnews.pstatic.net/image/origin/011/2026/03/11/4598294.jpg"
            },
            {
              "id": "0810003625049",
              "officeId": "081",
              "articleId": "0003625049",
              "officeName": "서울신문",
              "datetime": "202603112251",
              "type": "1",
              "title": "리사 수 AMD CEO 이달 방한…삼성·네이버 등과 협력 논의",
              "body": "AMD의 리사 수 최고경영자가 방한해 삼성전자·네이버 등과 협력 방안을 논의한다.",
              "photoType": "1",
              "imageOriginLink": "https://imgnews.pstatic.net/image/origin/081/2026/03/11/3625049.jpg"
            }
          ]
        },
        {
          "itemTotal": "1",
          "items": [
            {
              "id": "0010015953389",
              "officeId": "001",
              "articleId": "0015953389",
              "officeName": "연합뉴스",
              "datetime": "202603112345",
              "type": "1",
              "title": "뉴욕증시, 이란 전쟁·CPI 주목하며 혼조 출발",
              "body": "뉴욕증시의 3대 주가지수는 미국과 이란 간의 전쟁 진행 상황과 2월 소비자물가지수 발표에 주목하며 혼조세로 출발했다.",
              "photoType": "1",
              "imageOriginLink": "https://imgnews.pstatic.net/image/origin/001/2026/03/11/15953389.jpg"
            }
          ]
        }
      ]
    }
    """

    const val JSON_FOREIGN_STOCK_NEWS_SUCCESS = """
    [
      {
        "oid": "fnGuide",
        "ohnm": "로이터",
        "aid": "2512970",
        "tit": "엔비디아의 지원을 받는 스타트업 신틸 포토닉스, 고객과 함께 레이저 칩 테스트 시작",
        "dt": "20260311231233",
        "updatedt": "20260311231233",
        "editor": "Reporting by Stephen Nellis in San Francisco",
        "copyright": "(c) Copyright Thomson Reuters 2020.",
        "type": "0",
        "subcontent": "엔비디아의 지원을 받는 프랑스 스타트업 신틸 포토닉스는 고객들에게 테스트용 레이저 칩을 제공하기 시작했다고 발표했다.",
        "thumbUrl": null,
        "relatedItems": [
          {
            "reutersCode": "NVDA.O",
            "itemName": "NVDA",
            "fluctuationsRatio": "0.4655",
            "endUrl": "https://stock.naver.com/worldstock/stock/NVDA.O/price"
          }
        ]
      },
      {
        "oid": "fnGuide",
        "ohnm": "로이터",
        "aid": "2512806",
        "tit": "엔비디아, AI 클라우드 기업 네비우스에 20억 달러 투자",
        "dt": "20260311211942",
        "updatedt": "20260311211942",
        "editor": "Reporting by Arsheeya Bajwa in Bengaluru",
        "copyright": "(c) Copyright Thomson Reuters 2020.",
        "type": "0",
        "subcontent": "엔비디아는 인공지능 클라우드 회사 네비우스에 20억 달러를 투자할 것이라고 발표했다.",
        "thumbUrl": null,
        "relatedItems": [
          {
            "reutersCode": "NBIS.O",
            "itemName": "NBIS",
            "fluctuationsRatio": "14.1733",
            "endUrl": "https://stock.naver.com/worldstock/stock/NBIS.O/price"
          }
        ]
      }
    ]
    """

    const val JSON_FOREIGN_STOCK_OVERVIEW_SUCCESS = """
    {
      "companyName": "엔비디아",
      "companyNameEng": "NVIDIA Corp",
      "summary": "엔비디아는 풀스택 컴퓨팅 인프라 회사다.<br>컴퓨팅 및 네트워킹과 그래픽 부문으로 운영한다.",
      "summaries": {
        "summary": "엔비디아는 풀스택 컴퓨팅 인프라 회사다.<br>컴퓨팅 및 네트워킹과 그래픽 부문으로 운영한다.",
        "representativeName": "Jen-Hsun Huang",
        "representativeId": "PRE",
        "nation": "United States",
        "employees": 42000,
        "employeesLastUpdated": "2026-01-25",
        "city": "SANTA CLARA, CA",
        "address": "2788 San Tomas Expressway",
        "url": "https://www.nvidia.com/"
      },
      "industry": {
        "code": "57101010",
        "industryGroupKor": "반도체",
        "name": "INDUSTRY57101010"
      },
      "stockItemListedInfo": {
        "stockExchangeType": {
          "code": "NSQ",
          "zoneId": "EST5EDT",
          "nationType": "USA",
          "delayTime": 0,
          "startTime": "0930",
          "endTime": "1600",
          "closePriceSendTime": "2031",
          "nameKor": "나스닥 증권거래소",
          "nameEng": "NASDAQ Stock Exchange",
          "stockType": "worldstock",
          "nationName": "미국",
          "nationCode": "USA",
          "name": "NASDAQ"
        },
        "stockExchange": "나스닥 증권거래소",
        "currency": "USD",
        "accountDate": "2026-01-25",
        "listedAt": "1999-01-22T16:00:00-05:00",
        "countOfListedStock": 24300000000,
        "marketValue": "4,533,162,570",
        "marketValueKrw": "6,678조 2,551억원",
        "marketValueFull": "4,533,162,570,000"
      },
      "ownerInfoList": [
        {
          "investorName": "The Vanguard Group, Inc.",
          "position": 2255253323,
          "outstanding": "9.28%"
        }
      ]
    }
    """

    const val JSON_FOREIGN_STOCK_BASIC_SUCCESS = """
    {
      "stockEndType": "stock",
      "reutersCode": "NVDA.O",
      "stockName": "엔비디아",
      "stockNameEng": "NVIDIA Corp",
      "symbolCode": "NVDA",
      "stockExchangeType": {
        "code": "NSQ",
        "zoneId": "EST5EDT",
        "nationType": "USA",
        "delayTime": 0,
        "startTime": "0930",
        "endTime": "1600",
        "closePriceSendTime": "2031",
        "nameKor": "나스닥 증권거래소",
        "nameEng": "NASDAQ Stock Exchange",
        "nationName": "미국",
        "stockType": "worldstock",
        "nationCode": "USA",
        "name": "NASDAQ"
      },
      "stockExchangeName": "NASDAQ",
      "industryCodeType": {
        "code": "57101010",
        "industryGroupKor": "반도체",
        "name": "INDUSTRY57101010"
      },
      "closePrice": "185.62",
      "compareToPreviousClosePrice": "0.86",
      "fluctuationsRatio": "0.47",
      "localTradedAt": "2026-03-11T11:20:29-04:00",
      "marketStatus": "OPEN",
      "currencyType": {
        "code": "USD",
        "text": "US dollar",
        "name": "USD"
      },
      "countOfListedStock": 24300000000,
      "endUrl": "https://m.stock.naver.com/worldstock/stock/NVDA.O",
      "stockItemTotalInfos": [
        {
          "code": "marketValue",
          "key": "시총",
          "value": "4조 5,106억 USD",
          "valueDesc": "6,652조 1,827억원"
        },
        {
          "code": "highPriceOf52Weeks",
          "key": "52주 최고",
          "keyDesc": "2025.10.29.",
          "value": "212.19"
        },
        {
          "code": "lowPriceOf52Weeks",
          "key": "52주 최저",
          "keyDesc": "2025.04.07.",
          "value": "86.62"
        },
        {
          "code": "per",
          "key": "PER",
          "keyDesc": "2026.01.",
          "value": "37.73배"
        },
        {
          "code": "pbr",
          "key": "PBR",
          "keyDesc": "2026.01.",
          "value": "28.68배"
        },
        {
          "code": "dividendYieldRatio",
          "key": "배당수익률",
          "keyDesc": "2026.04.",
          "value": "0.02%"
        }
      ]
    }
    """

    const val JSON_NEWS_AGGREGATE_RANKING_SUCCESS = """
    {
      "rankingNews": [
        {
          "title": "랭킹 뉴스 테스트",
          "url": "https://n.news.naver.com/article/015/0005258767",
          "rank": "1",
          "press": "한국경제",
          "time": "1시간 전"
        }
      ]
    }
    """

    const val JSON_NEWS_HOME_SUCCESS = """
    {
      "flashNews": [
        {
          "url": "https://n.news.naver.com/article/018/0006231338",
          "thumbnailUrl": "https://imgnews.pstatic.net/image/origin/018/2026/03/09/6231338.jpg",
          "title": "감산에 100달러 넘어선 국제유가",
          "leadtext": "국제유가가 100달러 선에서 거래되고 있다.",
          "press": "이데일리",
          "time": "2026-03-09T23:50:14",
          "isVideo": false,
          "isOverseas": false
        }
      ],
      "mainNews": [
        {
          "url": "https://n.news.naver.com/article/001/0015948546",
          "thumbnailUrl": "https://imgnews.pstatic.net/image/origin/001/2026/03/09/15948546.jpg",
          "title": "뉴욕증시, 유가 100달러 돌파에 하락 출발",
          "leadtext": "뉴욕증시 3대 지수가 일제히 하락 출발했다.",
          "press": "연합뉴스",
          "time": "2026-03-09T23:28:53",
          "isVideo": false,
          "isOverseas": false
        }
      ],
      "rankingNews": [
        {
          "title": "랭킹 뉴스 테스트",
          "url": "https://n.news.naver.com/article/015/0005258767",
          "rank": "1",
          "press": "한국경제",
          "time": "1시간 전",
          "rankDiff": 2
        }
      ],
      "overseasNews": [
        {
          "url": "/news/worldnews/2509353",
          "title": "미국 원자력 발전 용량 증가",
          "officeHname": "로이터",
          "datetime": "25분 전",
          "aid": "2509353",
          "subcontent": "해외 시장 관련 기사 요약입니다.",
          "isVideo": false
        }
      ],
      "newsFocus": [
        {
          "categoryUrl": "/news/section?tab=market-outlook",
          "category": "시황·전망",
          "news": [
            {
              "title": "유가 119달러 쇼크에 아시아증시 패닉",
              "press": "매일경제",
              "time": "2026-03-09T13:28:08.000Z",
              "url": "https://n.news.naver.com/mnews/article/009/0005647629",
              "thumbnailUrl": "http://imgnews.naver.net/image/thumb154/009/2026/03/09/5647629.jpg",
              "isVideo": false
            }
          ]
        }
      ],
      "moneyStory": [
        {
          "url": "https://story.pay.naver.com/content/2285_3_C1",
          "photo": {
            "src": "https://moneystory-phinf.pstatic.net/sample.jpg",
            "alt": "머니스토리 이미지"
          },
          "title": "100달러 돌파한 유가, 이번 주 시행",
          "categoryName": "경제 브리핑 · Daily 국내 경제",
          "date": "2026. 03. 09.",
          "viewCount": 21117
        }
      ],
      "newsNotice": {
        "items": [
          {
            "no": "1609348",
            "comment": "-",
            "datetime": "2026-03-10",
            "title": "티웨이항공 공매도 과열종목 지정",
            "itemName": "티웨이항공",
            "itemcode": "091810",
            "causeCode": "03040",
            "noticeTypeName": "시장조치",
            "contents": "<html><body><p>공매도 거래 금지 적용</p></body></html>"
          }
        ],
        "totalElements": "769"
      }
    }
    """

    const val JSON_NOTICE_LIST_SUCCESS = """
    [
      {
        "noticeId": "142",
        "title": "미국/유럽 서머타임 적용에 따른 거래시간 변경 안내",
        "category": "거래시간",
        "categoryColor": "green",
        "createdAt": "2026-03-09T14:18:12"
      },
      {
        "noticeId": "141",
        "title": "코스피 서킷브레이커 발동, 코스닥 매도 사이드카",
        "category": "주의",
        "categoryColor": "red",
        "createdAt": "2026-03-09T10:46:08"
      }
    ]
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

    const val JSON_RESEARCH_AGGREGATE_SUCCESS = """
    {
      "researchCategory": [
        {
          "url": "/research/daily",
          "category": "시황정보",
          "report": [
            {
              "url": "/research/daily/35256",
              "title": "3/10 KB 리서치 모닝코멘트",
              "iconName": "kb",
              "securitiesCompany": "KB증권",
              "publishDate": "2026-03-10",
              "analystName": [],
              "isToday": false
            }
          ]
        },
        {
          "url": "/research/company",
          "category": "종목분석",
          "report": [
            {
              "url": "/research/company/90866",
              "title": "나올 재료 감안 시 이번엔 텐베거가 예상됩니다",
              "iconName": "hana",
              "securitiesCompany": "하나증권",
              "publishDate": "2026-03-10",
              "analystName": [],
              "isToday": false
            }
          ]
        }
      ]
    }
    """

    const val JSON_RESEARCH_RECENT_POPULAR_SUCCESS = """
    [
      {
        "researchCategory": "종목분석",
        "category": "종목분석",
        "itemCode": "218410",
        "itemName": "RFHIC",
        "researchId": "90866",
        "title": "나올 재료 감안 시 이번엔 텐베거가 예상됩니다",
        "content": "<p><strong>12개월 목표가 10만원으로 상향</strong></p>",
        "brokerName": "하나증권",
        "brokerCode": "57",
        "writeDate": "2026-03-10",
        "readCount": "85",
        "endUrl": "https://m.stock.naver.com/research/company/90866",
        "opinion": "Buy",
        "goalPrice": "100000",
        "prevGoalPrice": "63200"
      },
      {
        "researchCategory": "시황정보",
        "category": "시황정보",
        "researchId": "35256",
        "title": "3/10 KB 리서치 모닝코멘트",
        "content": "<p><strong>국내 및 글로벌 증시 동향</strong></p>",
        "brokerName": "KB증권",
        "brokerCode": "58",
        "writeDate": "2026-03-10",
        "readCount": "84",
        "endUrl": "https://m.stock.naver.com/research/daily/35256"
      }
    ]
    """

    const val JSON_RESEARCH_CATEGORY_LATEST_SUCCESS = """
    {
      "MARKET": [
        {
          "researchCategory": "시황정보",
          "category": "시황정보",
          "researchId": "35256",
          "title": "3/10 KB 리서치 모닝코멘트",
          "content": "<p><strong>국내 및 글로벌 증시 동향</strong></p>",
          "brokerName": "KB증권",
          "brokerCode": "58",
          "writeDate": "2026-03-10",
          "readCount": "84",
          "endUrl": "https://m.stock.naver.com/research/daily/35256"
        }
      ],
      "COMPANY": [
        {
          "researchCategory": "종목분석",
          "category": "종목분석",
          "itemCode": "218410",
          "itemName": "RFHIC",
          "researchId": "90866",
          "title": "나올 재료 감안 시 이번엔 텐베거가 예상됩니다",
          "content": "<p><strong>12개월 목표가 10만원으로 상향</strong></p>",
          "brokerName": "하나증권",
          "brokerCode": "57",
          "writeDate": "2026-03-10",
          "readCount": "85",
          "endUrl": "https://m.stock.naver.com/research/company/90866",
          "opinion": "Buy",
          "goalPrice": "100000",
          "prevGoalPrice": "63200"
        }
      ],
      "INDUSTRY": [],
      "INVEST": [],
      "ECONOMY": [],
      "DEBENTURE": []
    }
    """

    const val JSON_RESEARCH_INDUSTRY_SUCCESS = """
    {
      "자동차": [
        {
          "researchCategory": "산업분석",
          "category": "자동차",
          "researchId": "43690",
          "title": "1월 Auto Sales DataBook",
          "content": "<p><strong>1월 글로벌 도매 판매 현대차 -1.0%, 기아 +2.5%</strong></p>",
          "analyst": "이재일",
          "brokerName": "유진투자증권",
          "brokerCode": "63",
          "writeDate": "2026-03-10",
          "readCount": "6",
          "endUrl": "https://m.stock.naver.com/research/industry/43690"
        }
      ],
      "게임": [
        {
          "researchCategory": "산업분석",
          "category": "게임",
          "researchId": "43689",
          "title": "구글 수수료율 인하에 따른 업종 전반 수혜 기대",
          "content": "<p>구글 수수료 개편 수혜 예상</p>",
          "analyst": "남효지",
          "brokerName": "SK증권",
          "brokerCode": "64",
          "writeDate": "2026-03-10",
          "readCount": "31",
          "endUrl": "https://m.stock.naver.com/research/industry/43689"
        }
      ]
    }
    """

    const val JSON_DISCUSSION_RANKING_SUCCESS = """
    {
      "rankTime": "2026-03-10T08:00:00",
      "totalCount": 100,
      "contents": [
        {
          "itemCode": "005930",
          "ranking": 1,
          "prevRanking": 3,
          "score": 604,
          "rankTime": "2026-03-10T08:00:00",
          "posts": [
            {
              "postId": "414379193",
              "title": "증권사 객장에 가 보세요",
              "contentSwReplacedButImg": "객장 분위기가 정상이 아닙니다.",
              "postType": "normal"
            }
          ],
          "stockPrices": {
            "stockName": "삼성전자",
            "endUrl": "https://m.stock.naver.com/domestic/stock/005930",
            "overMarketPriceInfo": {
              "overPrice": "187,900",
              "fluctuationsRatio": "8.30"
            }
          }
        },
        {
          "itemCode": "247540",
          "ranking": 2,
          "score": 439,
          "rankTime": "2026-03-10T08:00:00",
          "posts": [
            {
              "postId": "414531273",
              "title": "거참 이상하네,,왜 에코프로비엠만 올랐지?",
              "contentSwReplacedButImg": "삼성sdi, 포스코퓨처엠은 폭락 그대로인데...",
              "postType": "normal"
            }
          ],
          "stockPrices": {
            "stockName": "에코프로비엠",
            "endUrl": "https://m.stock.naver.com/domestic/stock/247540",
            "overMarketPriceInfo": {
              "overPrice": "215,000",
              "fluctuationsRatio": "6.17"
            }
          }
        }
      ]
    }
    """

    const val JSON_RESEARCH_RANKING_SEARCH_TOP_SUCCESS = """
    {
      "ranking": [
        {
          "itemname": "삼성전자",
          "itemcode": "005930",
          "marketStatus": "PREOPEN",
          "nowVal": "173500",
          "changeRate": "0.0",
          "per": "26.43",
          "pbr": "2.94075",
          "dividendRate": "0.961",
          "marketSum": "1027057179000000"
        },
        {
          "itemname": "SK하이닉스",
          "itemcode": "000660",
          "marketStatus": "PREOPEN",
          "nowVal": "836000",
          "changeRate": "0.0",
          "per": "17.05",
          "pbr": "6.38073",
          "dividendRate": "0.359",
          "marketSum": "595819177000000"
        }
      ],
      "latestResearch": [
        {
          "itemCode": "005930",
          "itemName": "삼성전자",
          "researchId": "90857",
          "title": "주가 매력도가 더 높아졌다",
          "content": "<p><strong>동사에 대한 투자의견과 목표주가 유지</strong></p>",
          "brokerName": "미래에셋증권",
          "brokerCode": "56",
          "writeDate": "2026-03-10",
          "readCount": "743",
          "endUrl": "https://stock.pstatic.net/stock-research/company/56/20260310_company_716140000.pdf",
          "opinion": "매수",
          "goalPrice": "275000",
          "prevGoalPrice": "173500"
        }
      ]
    }
    """

    const val JSON_RESEARCH_RANKING_PRICE_TOP_SUCCESS = """
    {
      "ranking": [
        {
          "itemname": "한화시스템",
          "itemcode": "272210",
          "marketStatus": "PREOPEN",
          "nowVal": "162700",
          "changeRate": "0.0",
          "per": "51.63",
          "pbr": "6.24839",
          "dividendRate": "0.307",
          "marketSum": "30737184000000"
        }
      ],
      "latestResearch": [
        {
          "itemCode": "272210",
          "itemName": "한화시스템",
          "researchId": "91234",
          "title": "방산과 우주 모멘텀 점검",
          "content": "<p><strong>수주 가시성과 밸류 재평가</strong></p>",
          "brokerName": "신한투자증권",
          "brokerCode": "21",
          "writeDate": "2026-03-10",
          "readCount": "125",
          "endUrl": "https://stock.pstatic.net/stock-research/company/21/20260310_company_1234000.pdf",
          "opinion": "매수",
          "goalPrice": "190000",
          "prevGoalPrice": "150000"
        }
      ]
    }
    """

    const val JSON_RESEARCH_RANKING_UP_SUCCESS = JSON_RESEARCH_RANKING_PRICE_TOP_SUCCESS

    const val JSON_RESEARCH_RANKING_DOWN_SUCCESS = JSON_RESEARCH_RANKING_SEARCH_TOP_SUCCESS

    const val JSON_STOCK_RESEARCH_SUCCESS = """
    [
      {
        "nid": "90857",
        "itemcode": "005930",
        "itemname": "삼성전자",
        "brokerName": "미래에셋증권",
        "brokerCode": "56",
        "title": "주가 매력도가 더 높아졌다",
        "content": "<p><strong>동사에 대한 투자의견과 목표주가 유지</strong></p>",
        "goalPrice": "275000",
        "opinion": "매수",
        "attachUrl": "https://stock.pstatic.net/stock-research/company/56/20260310_company_716140000.pdf",
        "readCount": "743",
        "writeDate": "2026-03-10",
        "prevGoalPrice": "173500"
      },
      {
        "nid": "90814",
        "itemcode": "005930",
        "itemname": "삼성전자",
        "brokerName": "미래에셋증권",
        "brokerCode": "56",
        "title": "후퇴가 아닌 진격을 할 때",
        "content": "<p><strong>급격한 주가 하락에도 목표주가 유지</strong></p>",
        "goalPrice": "275000",
        "opinion": "매수",
        "attachUrl": "https://stock.pstatic.net/stock-research/company/56/20260305_company_761607000.pdf",
        "readCount": "34312",
        "writeDate": "2026-03-05",
        "prevGoalPrice": "172200"
      }
    ]
    """

}
