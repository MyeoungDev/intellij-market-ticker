package com.github.myeoungdev.marketticker.fixtures.naver

import com.github.myeoungdev.marketticker.infrastructure.naver.*

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-04
 */
object NaverFixtures {

    /**
     * NaverSearchItem (검색 결과 단건) 생성 팩토리
     */
    fun createNaverSearchItem(
        code: String = "005930",
        name: String = "삼성전자",
        typeCode: String = "KOSPI",
        typeName: String = "코스피",
        url: String = "/domestic/stock/005930/total",
        reutersCode: String = "005930",
        nationCode: String = "KOR",
        nationName: String = "대한민국",
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
     * NaverResponse (공통 래퍼) 생성 팩토리
     * 제네릭 T를 받아 다양한 Payload를 감쌀 수 있게 함
     */
    fun <T> createNaverResponse(
        result: T?,
        isSuccess: Boolean = true,
        message: String = "",
        detailCode: String = ""
    ): NaverSearchResponse<T> {
        return NaverSearchResponse(
            isSuccess = isSuccess,
            detailCode = detailCode,
            message = message,
            result = result
        )
    }

    /**
     * 검색 결과 Payload 생성 팩토리
     */
    fun createSearchResultPayload(
        query: String = "삼성",
        items: List<NaverSearchItem> = listOf(createNaverSearchItem())
    ): NaverSearchResultPayload {
        return NaverSearchResultPayload(
            query = query,
            items = items
        )
    }

    /**
     * NaverStockPrice (종목 상세 정보) 생성 팩토리
     * - 자주 변하는 값(가격, 이름)만 매개변수로 노출하고 나머지는 기본값 처리
     */
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

    /**
     * StockExchangeType (거래소 정보) 생성 팩토리
     */
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

    /**
     * CurrencyResponse (통화 정보) 생성 팩토리
     */
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