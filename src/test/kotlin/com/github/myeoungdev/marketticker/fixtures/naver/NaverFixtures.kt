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
     * NaverSearchResponse (최상위 응답 래퍼) 생성 팩토리
     */
    fun createNaverSearchResponse(
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
     * SearchResultPayload (검색 결과 컨테이너) 생성 팩토리
     * - items에 빈 리스트나 커스텀 리스트를 넣어 테스트 가능
     */
    fun createSearchResultPayload(
        query: String = "삼성",
        items: List<NaverSearchItem> = listOf(createNaverSearchItem())
    ): SearchResultPayload {
        return SearchResultPayload(
            query = query,
            items = items
        )
    }

    /**
     * NaverSearchItem (개별 종목 정보) 생성 팩토리
     * - 자주 쓰이는 삼성전자(KOSPI)를 기본값으로 설정
     */
    fun createNaverSearchItem(
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

    // -------------------------------------------------------------------------
    // 아래는 시세(Price) 관련 Fixture (기존 코드 유지 및 필요시 사용)
    // -------------------------------------------------------------------------

    fun createNaverStockPrice(
        itemCode: String = "005930",
        stockName: String = "삼성전자",
        closePrice: String = "70,000",
        compareToPreviousClosePrice: String = "1,000",
        fluctuationsRatio: String = "1.45",
        accumulatedTradingVolume: String = "15,000,000",
        accumulatedTradingValue: String = "1,000,000백만",
        marketStatus: String = "OPEN",
        stockExchangeType: StockExchangeType = createStockExchangeType(),
        currencyType: CurrencyResponse = createCurrencyResponse(),
        openPrice: String = "69,000",
        highPrice: String = "71,000",
        lowPrice: String = "69,000",
        isinCode: String? = "KR7005930003"
    ): NaverStockPrice {
        return NaverStockPrice(
            itemCode = itemCode,
            stockName = stockName,
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

    fun createStockExchangeType(
        code: String = "KS",
        nameKor: String = "코스피",
        nameEng: String = "KOSPI",
        zoneId: String = "Asia/Seoul",
        nationType: String = "KOR"
    ): StockExchangeType {
        return StockExchangeType(
            code = code,
            zoneId = zoneId,
            nationType = nationType,
            delayTime = 0,
            startTime = "0900",
            endTime = "1530",
            closePriceSendTime = "1630",
            nameKor = nameKor,
            nameEng = nameEng,
            nationCode = "KR",
            nationName = "Korea",
            stockType = "domestic",
            name = nameEng
        )
    }

    fun createCurrencyResponse(
        code: String = "KRW",
        text: String = "Won",
        name: String = "Won"
    ): CurrencyResponse {
        return CurrencyResponse(
            code = code,
            text = text,
            name = name
        )
    }
}