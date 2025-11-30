package com.github.myeoungdev.intellijmarketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class Ticker(
    val symbol: String,
    val name: String,
    val market: String,
    val country: String
)
