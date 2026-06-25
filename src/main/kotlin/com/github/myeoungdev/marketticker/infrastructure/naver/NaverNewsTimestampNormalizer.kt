package com.github.myeoungdev.marketticker.infrastructure.naver

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Naver 뉴스 계열의 다양한 시간 문자열을 KST 기준 절대시각으로 정규화합니다.
 */
internal object NaverNewsTimestampNormalizer {

    private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    private val DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val LOCAL_DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    )
    private val LOCAL_DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd."),
        DateTimeFormatter.ofPattern("yyyy.MM.dd")
    )
    private val RELATIVE_TIME_PATTERN = Regex("""^(\d+)\s*(초|분|시간|일)\s*전$""")

    data class NormalizedTimestamp(
        val displayText: String,
        val instant: Instant?
    )

    fun normalize(raw: String?, clock: Clock = Clock.systemUTC()): NormalizedTimestamp {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isBlank()) {
            return NormalizedTimestamp(displayText = "", instant = null)
        }

        parseInstant(candidate, clock)?.let { instant ->
            return NormalizedTimestamp(
                displayText = DISPLAY_FORMATTER.format(instant.atZone(KST_ZONE)),
                instant = instant
            )
        }

        return NormalizedTimestamp(displayText = candidate, instant = null)
    }

    private fun parseInstant(raw: String, clock: Clock): Instant? {
        parseRelative(raw, clock)?.let { return it }

        parseAbsoluteInstant(raw)?.let { return it }

        parseLocalDateTime(raw)?.let { return it.atZone(KST_ZONE).toInstant() }

        parseLocalDate(raw)?.let { return it.atStartOfDay(KST_ZONE).toInstant() }

        return null
    }

    private fun parseRelative(raw: String, clock: Clock): Instant? {
        when (raw) {
            "방금 전" -> return clock.instant()
        }

        val match = RELATIVE_TIME_PATTERN.matchEntire(raw) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = when (match.groupValues[2]) {
            "초" -> java.time.temporal.ChronoUnit.SECONDS
            "분" -> java.time.temporal.ChronoUnit.MINUTES
            "시간" -> java.time.temporal.ChronoUnit.HOURS
            "일" -> java.time.temporal.ChronoUnit.DAYS
            else -> return null
        }
        return clock.instant().minus(amount, unit)
    }

    private fun parseAbsoluteInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    ZonedDateTime.parse(raw).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private fun parseLocalDateTime(raw: String): LocalDateTime? {
        val candidate = raw
        for (formatter in LOCAL_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(candidate, formatter)
            } catch (_: DateTimeParseException) {
                // try next formatter
            }
        }
        return null
    }

    private fun parseLocalDate(raw: String): LocalDate? {
        val candidate = raw.replace(" ", "")
        for (formatter in LOCAL_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(candidate, formatter)
            } catch (_: DateTimeParseException) {
                // try next formatter
            }
        }
        return null
    }
}
