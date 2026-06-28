package com.github.myeoungdev.marketticker.infrastructure.cnn

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.myeoungdev.marketticker.application.provider.MarketIndicatorProvider
import com.github.myeoungdev.marketticker.common.config.httpClient
import com.github.myeoungdev.marketticker.common.config.objectMapper
import com.github.myeoungdev.marketticker.domain.model.IndicatorCategory
import com.github.myeoungdev.marketticker.domain.model.MarketIndicator
import com.github.myeoungdev.marketticker.domain.model.MarketStatus
import com.intellij.openapi.application.ApplicationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val logger = KotlinLogging.logger {}

/**
 * CNN Fear & Greed 지수를 조회하는 provider 입니다.
 */
class CnnFearGreedProvider(
    private val client: HttpClient = httpClient,
    private val endpoint: String = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata",
) : MarketIndicatorProvider {

    override fun getIndicators(): List<MarketIndicator> {
        checkBackgroundThread()

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://money.cnn.com/data/fear-and-greed/")
                .header("Origin", "https://money.cnn.com")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error { "CNN fear & greed API Error [${response.statusCode()}]: $endpoint" }
                return emptyList()
            }

            val payload: CnnFearGreedResponse = objectMapper.readValue(response.body())
            payload.fearAndGreed?.toMarketIndicator()?.let(::listOf) ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch CNN fear & greed index" }
            emptyList()
        }
    }

    private companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private fun checkBackgroundThread() {
        val app = ApplicationManager.getApplication()
        if (app != null && app.isDispatchThread) {
            logger.error { "Network call on EDT! This will freeze the UI." }
            throw IllegalStateException("Network operations must not be performed on the EDT.")
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CnnFearGreedResponse(
    @JsonProperty("fear_and_greed")
    val fearAndGreed: CnnFearGreedSnapshot? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CnnFearGreedSnapshot(
    val score: Double,
    val rating: String
) {

    fun toMarketIndicator(): MarketIndicator {
        val ratingLabel = rating.toSentimentLabel()
        return MarketIndicator(
            code = "FNG",
            name = "Fear & Greed",
            currentPrice = score,
            changeRate = 0.0,
            marketStatus = MarketStatus.CLOSED,
            category = IndicatorCategory.SENTIMENT,
            unit = null,
            sentimentScore = score,
            sentimentLabel = ratingLabel,
            displayHint = ratingLabel
        )
    }

    private fun String.toSentimentLabel(): String {
        return when (trim().lowercase()) {
            "extreme fear" -> "극단적 공포"
            "fear" -> "공포"
            "neutral" -> "중립"
            "greed" -> "탐욕"
            "extreme greed" -> "극단적 탐욕"
            else -> trim()
        }
    }
}
