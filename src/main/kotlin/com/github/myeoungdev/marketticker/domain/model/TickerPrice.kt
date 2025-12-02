package com.github.myeoungdev.marketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-02
 */
data class TickerPrice(
    val symbol: String,
    val price: Double,
    val changeRate: Double,
    val volume: Long,
    val marketType: String
)