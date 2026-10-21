package com.github.myeoungdev.marketticker.infrastructure.naver

import com.fasterxml.jackson.databind.JsonNode
import com.github.myeoungdev.marketticker.application.provider.EtfProvider
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.etf.EtfComponent
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDetail
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDistribution
import com.github.myeoungdev.marketticker.domain.model.etf.EtfDistributionHistory
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
        val baseNode = request(base)?.let(::parse)?.let { findObject(it, setOf("etfBase", "item", "result")) } ?: return null
        val summary = toSummary(baseNode, code) ?: return null
        val componentResponse = request(componentsUrl)?.let(::parse)
        val componentNodes = componentResponse?.let { findArray(it, setOf("items", "contents", "components")) }.orEmpty()
        val components = componentNodes.mapNotNull(::toComponent)
        return EtfDetail(summary, components, componentResponse?.let { isComplete(it, componentNodes.size) } ?: false,
            allocations = toAllocations(baseNode),
            allocationReferenceDate = baseNode.text("referenceDate", "baseDate", "date"))
    }

    override fun getDomesticEtfDistributions(code: String): EtfDistributionHistory {
        if (!code.matches(Regex("[0-9A-Z]{4,12}"))) return EtfDistributionHistory()
        val historyUrl = "https://stock.naver.com/api/domestic/detail/$code/ETFDividendHist?startIdx=0&pageSize=100"
        val summaryUrl = "https://stock.naver.com/api/domestic/detail/$code/ETFDividend"
        val events = request(historyUrl)?.let(::parse)?.let { findArray(it, setOf("items", "contents", "history")) }
            .orEmpty().mapNotNull(::toDistribution)
        val summary = request(summaryUrl)?.let(::parse)
        return EtfDistributionHistory(
            items = events,
            amountTtm = summary?.number("dividendPerShareTtm"),
            yieldTtm = summary?.number("dividendYieldTtm"),
            countThisYear = summary?.number("dividendCountThisYear")?.toInt(),
            monthsThisYear = summary?.text("dividendMonthsThisYear"),
            countLastYear = summary?.number("dividendCountLastYear")?.toInt(),
            monthsLastYear = summary?.text("dividendMonthsLastYear")
        )
    }

    private fun parse(json: String): JsonNode? = runCatching { objectMapper.readTree(json) }.getOrNull()

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
            node.text("referenceDate", "baseDate", "date"),
            node.number("iNav", "inav", "indicativeNav"),
            node.number("fundflowDay"), node.number("fundflowWeek"), node.number("fundflowMonth"),
            node.number("fundflow3Month"), node.number("fundflow6Month"), node.number("fundflowYear"), node.number("fundflowYTD"),
            node.number("returnRate9m", "performanceMarket9Month", "performanceNav9Month")
        )
    }

    private fun toAllocations(node: JsonNode): List<com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation> {
        val result = mutableListOf<com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation>()
        val sectorItems = findArray(node, setOf("sectorPortfolioList", "sectors"))
        sectorItems.forEach { item ->
            val name = item.text("sectorName", "name", "sector", "industryName") ?: return@forEach
            val weight = item.number("sectorWeight", "weight", "weightRatio", "ratio") ?: return@forEach
            result += com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation(name, weight,
                com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.SECTOR)
        }
        if (node.isObject) {
            val fields = node.fields()
            while (fields.hasNext()) {
                val (key, value) = fields.next()
                if (value.isNull || value.isContainerNode) continue
                val weight = value.asText().replace(",", "").replace("%", "").toDoubleOrNull() ?: continue
                val kind = when {
                    key.startsWith("country", true) && key.contains("Weight", true) -> com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.COUNTRY
                    key.startsWith("asset", true) && key.contains("Weight", true) -> com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.ASSET
                    else -> continue
                }
                val prefix = if (kind == com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation.Kind.COUNTRY) "country" else "asset"
                val label = key.removePrefix(prefix).removeSuffix("Weight").replace(Regex("([a-z])([A-Z])"), "$1 $2")
                result += com.github.myeoungdev.marketticker.domain.model.etf.EtfAllocation(label, weight, kind)
            }
        }
        return result
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

    private fun toDistribution(node: JsonNode): EtfDistribution? {
        val date = node.path("id").text("exDividendAt", "exDividendDate", "recordDate")
            ?: node.text("exDividendAt", "exDividendDate", "recordDate") ?: return null
        val amount = node.number("dividendAmount", "distributionAmount", "dividendPerShare") ?: return null
        return EtfDistribution(date, amount, node.number("dividendYield", "distributionYield"))
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

    private fun isComplete(node: JsonNode, itemCount: Int): Boolean {
        if (node.isObject) {
            node.get("hasNext")?.takeIf { it.isBoolean }?.let { return !it.asBoolean() }
            node.get("hasMore")?.takeIf { it.isBoolean }?.let { return !it.asBoolean() }
            val total = node.get("totalCount")?.takeIf { it.isNumber }?.asInt()
                ?: node.get("totalElements")?.takeIf { it.isNumber }?.asInt()
            if (total != null) return total <= itemCount
            for (child in node) {
                if (child.isObject) {
                    val result = completenessFromMetadata(child, itemCount)
                    if (result != null) return result
                }
            }
        }
        return false
    }

    private fun completenessFromMetadata(node: JsonNode, itemCount: Int): Boolean? {
        node.get("hasNext")?.takeIf { it.isBoolean }?.let { return !it.asBoolean() }
        node.get("hasMore")?.takeIf { it.isBoolean }?.let { return !it.asBoolean() }
        val total = node.get("totalCount")?.takeIf { it.isNumber }?.asInt()
            ?: node.get("totalElements")?.takeIf { it.isNumber }?.asInt()
        return total?.let { it <= itemCount }
    }

    private fun JsonNode.text(vararg names: String): String? {
        for (key in names) get(key)?.takeIf { !it.isNull && !it.isContainerNode }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun JsonNode.number(vararg names: String): Double? = text(*names)?.replace(",", "")?.replace("%", "")?.toDoubleOrNull()

}
