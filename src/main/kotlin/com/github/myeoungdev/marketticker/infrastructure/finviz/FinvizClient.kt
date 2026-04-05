package com.github.myeoungdev.marketticker.infrastructure.finviz

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
import com.github.myeoungdev.marketticker.domain.model.calendar.MarketCalendarEvent
import com.intellij.openapi.application.ApplicationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val logger = KotlinLogging.logger {}

class FinvizClient(
    private val client: HttpClient = httpClient,
    private val calendarBaseUrl: String = "https://finviz.com/calendar"
) {

    companion object {
        private const val USER_AGENT_KEY = "User-Agent"
        private const val USER_AGENT_VALUE = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val ACCEPT_KEY = "Accept"
        private const val ACCEPT_VALUE = "text/html,application/json"
    }

    fun fetchCalendar(type: CalendarType, limit: Int = 50): List<MarketCalendarEvent> {
        checkBackgroundThread()
        return runCatching {
            val html = getText("$calendarBaseUrl/${type.path}")
            when (type) {
                CalendarType.EARNINGS -> {
                    val payload: FinvizCalendarRouteData<FinvizEarningsCalendarData> = readRouteInitData(html)
                    payload.data.entries.items.take(limit).map { it.toDomain() }
                }
                CalendarType.ECONOMIC -> {
                    val payload: FinvizCalendarRouteData<FinvizEconomicCalendarData> = readRouteInitData(html)
                    payload.data.entries.take(limit).map { it.toDomain() }
                }
            }
        }.getOrElse {
            logger.error(it) { "Failed to fetch Finviz calendar for ${type.name}" }
            emptyList()
        }
    }

    private inline fun <reified T> readRouteInitData(html: String): T {
        val regex = Regex(
            "<script id=\"route-init-data\" type=\"application/json\">(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val json = regex.find(html)?.groupValues?.getOrNull(1)
            ?: error("route-init-data script not found")
        return objectMapper.readValue(json)
    }

    private fun getText(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(USER_AGENT_KEY, USER_AGENT_VALUE)
            .header(ACCEPT_KEY, ACCEPT_VALUE)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Finviz request failed: ${response.statusCode()} for $url" }
        return response.body()
    }

    private fun checkBackgroundThread() {
        val app = ApplicationManager.getApplication()
        if (app != null && app.isDispatchThread) {
            throw IllegalStateException("Network operations must not be performed on the EDT.")
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizCalendarRouteData<T>(
    val data: T
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizEarningsCalendarData(
    val entries: FinvizPagedEntries<FinvizEarningsEntry> = FinvizPagedEntries()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizPagedEntries<T>(
    val items: List<T> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizEarningsEntry(
    val earningsDate: String = "",
    val ticker: String = "",
    val company: String = "",
    val epsEstimate: Double? = null,
    val epsActual: Double? = null,
    val salesEstimate: Double? = null,
    val salesActual: Double? = null,
    val boxoverData: FinvizBoxoverData = FinvizBoxoverData()
) {
    fun toDomain(): MarketCalendarEvent {
        return MarketCalendarEvent(
            type = CalendarType.EARNINGS,
            dateTime = earningsDate,
            ticker = ticker.ifBlank { null },
            title = company.ifBlank { ticker },
            subtitle = listOfNotNull(
                boxoverData.industry.takeIf { it.isNotBlank() },
                boxoverData.country.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            actual = buildString {
                append("EPS ")
                append(epsActual?.toString() ?: "-")
                append(" / Sales ")
                append(salesActual?.toString() ?: "-")
            },
            forecast = buildString {
                append("EPS ")
                append(epsEstimate?.toString() ?: "-")
                append(" / Sales ")
                append(salesEstimate?.toString() ?: "-")
            },
            previous = "",
            impact = 2
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizBoxoverData(
    val country: String = "",
    val company: String = "",
    val industry: String = "",
    val marketCap: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizEconomicCalendarData(
    val entries: List<FinvizEconomicEntry> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FinvizEconomicEntry(
    val event: String = "",
    val category: String = "",
    val date: String = "",
    val actual: String? = null,
    val previous: String? = null,
    val forecast: String? = null,
    @JsonProperty("importance")
    val importance: Int = 0
) {
    fun toDomain(): MarketCalendarEvent {
        return MarketCalendarEvent(
            type = CalendarType.ECONOMIC,
            dateTime = date,
            ticker = null,
            title = event,
            subtitle = category,
            actual = actual.orEmpty(),
            forecast = forecast.orEmpty(),
            previous = previous.orEmpty(),
            impact = importance
        )
    }
}
