package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.databind.JsonNode
import com.github.myeoungdev.marketticker.application.provider.EtfProvider
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.etf.EtfComponent
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDetail
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private fun fetchNaverEtf(url: String): String? = runCatching {
    val response = httpClient.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
            .header("Accept", "application/json").header("Origin", "https://stock.naver.com").GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
    response.body().takeIf { response.statusCode() == 200 }
}.getOrNull()

/** Naver web ETF endpoints are intentionally isolated here because they have no public API contract. */
class NaverEtfProvider(
    private val request: (String) -> String? = ::fetchNaverEtf
) : EtfProvider {
    override fun getDomesticEtfs(page: Int, pageSize: Int): List<EtfSummary> {
        val url = "https://stock.naver.com/api/stockSecurity/etfs/v2/domestic?listingType=tradingValueDesc&size=${pageSize.coerceIn(1, 100)}&index=${page.coerceAtLeast(0)}"
        val root = request(url)?.let(::parse) ?: error("국내 ETF 목록을 가져오지 못했습니다.")
        return findArray(root, setOf("items", "contents", "etfs"))
            .mapNotNull(::toSummary)
    }

    override fun getDomesticEtfDetail(code: String): EtfDetail? {
        if (!code.matches(Regex("[0-9A-Z]{4,12}"))) return null
        val base = "https://stock.naver.com/api/domestic/detail/$code/ETFBase"
        val componentsUrl = "https://stock.naver.com/api/domestic/detail/$code/ETFComponent?startIdx=0&pageSize=100"
        val summary = request(base)?.let(::parse)?.let { findObject(it, setOf("etfBase", "item", "result")) }?.let { toSummary(it, code) }
            ?: return null
        val components = request(componentsUrl)?.let(::parse)?.let { findArray(it, setOf("items", "contents", "components")) }
            ?.mapNotNull(::toComponent).orEmpty()
        return EtfDetail(summary, components)
    }

    private fun parse(json: String): JsonNode = objectMapper.readTree(json)

    private fun toSummary(node: JsonNode, fallbackCode: String? = null): EtfSummary? {
        val code = node.text("itemCode", "code", "symbol", "itemcode") ?: fallbackCode ?: return null
        val name = node.text("itemName", "name", "itemname", "korName") ?: code
        return EtfSummary(
            code, name,
            node.number("currentPrice", "nowPrice", "price", "closePrice"),
            node.number("fluctuationsRatio", "changeRate", "prevChangeRate", "fluctuationRate"),
            node.number("returnRate1m", "performanceMarketMonth", "performanceNavMonth"),
            node.number("returnRate3m", "performanceMarket3Month", "performanceNav3Month"),
            node.number("returnRate6m", "performanceMarket6Month", "performanceNav6Month"),
            node.number("returnRate1y", "performanceMarketYear", "performanceNavYear"),
            node.number("returnRate3y", "performanceMarket3Year", "performanceNav3Year"),
            node.number("returnRate5y", "performanceMarket5Year", "performanceNav5Year"),
            node.number("returnRate10y", "performanceMarket10Year", "performanceNav10Year"),
            node.number("tradingValue", "accumulatedTradingValue", "tradeValue"),
            node.number("totalNetAssets", "totalAsset", "netAssets", "marketValue", "aum"),
            node.number("totalExpenseRatio", "totalFee", "feeRate", "expenseRatio"),
            node.number("distributionYield", "dividendYield", "dividendRate"),
            node.text("categoryName", "themeName", "category", "etfType"),
            node.text("companyName", "managerName", "issuerName", "issueName"),
            node.text("indexName", "trackingIndexName", "baseIndexName", "etfBaseIdx"),
            node.text("hedgeType", "currencyHedgeType", "hedgeYn"),
            node.text("leverageType", "etfType", "productType"),
            node.text("referenceDate", "baseDate", "date")
        )
    }

    private fun toComponent(node: JsonNode): EtfComponent? {
        val name = node.text("componentName", "itemName", "name", "stockName") ?: return null
        return EtfComponent(
            node.text("componentItemCode", "itemCode", "code", "symbol"), name,
            node.number("cuUnitQuantity", "quantity", "holdingQuantity", "componentCount"),
            node.number("evalAmount", "evaluationAmount", "marketValue", "amount"),
            node.number("weight", "weightRatio", "componentWeight", "ratio"),
            node.text("referenceDate", "baseDate", "date")
        )
    }

    private fun findArray(node: JsonNode, names: Set<String>): List<JsonNode> {
        if (node.isArray) return node.toList()
        if (node.isObject) {
            for (name in names) node.get(name)?.takeIf { it.isArray }?.let { return it.toList() }
            for (child in node) findArray(child, names).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    private fun findObject(node: JsonNode, names: Set<String>): JsonNode? {
        if (!node.isObject) return null
        for (name in names) node.get(name)?.takeIf { it.isObject }?.let { return it }
        for (child in node) findObject(child, names)?.let { return it }
        return node
    }

    private fun JsonNode.text(vararg names: String): String? {
        for (key in names) get(key)?.takeIf { !it.isNull && !it.isContainerNode }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun JsonNode.number(vararg names: String): Double? = text(*names)?.replace(",", "")?.replace("%", "")?.toDoubleOrNull()

}
