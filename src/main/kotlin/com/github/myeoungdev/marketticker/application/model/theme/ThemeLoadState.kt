package com.github.myeoungdev.marketticker.application.model.theme

import java.time.Duration
import java.time.Instant

/** 값이 비어 있으면 미조회 또는 실패이며, 빈 결과를 포함한 값이 있으면 조회에 성공한 상태입니다. */
data class ThemeLoadState<T>(
    val value: T? = null,
    val fetchedAt: Instant? = null,
    val isLoading: Boolean = false,
    val isStale: Boolean = false,
    val error: Throwable? = null
)

data class ThemeManualRefreshState(val isAvailable: Boolean, val retryAfter: Duration)

class ThemeRefreshThrottledException(val retryAfter: Duration) :
    IllegalStateException("Theme manual refresh is cooling down for $retryAfter")
