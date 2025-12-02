package com.github.myeoungdev.marketticker.domain.model

/**
 * Market Ticker 에서 사용하기 위한 Ticker 도메인
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class Ticker(
    val symbol: String,
    val name: String,
    val market: String,
    val country: String?
)
