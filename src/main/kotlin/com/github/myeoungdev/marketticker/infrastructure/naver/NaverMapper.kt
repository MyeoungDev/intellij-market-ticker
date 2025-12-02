package com.github.myeoungdev.marketticker.infrastructure.naver

import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
fun NaverSearchItem.toTicker(): Ticker =
    Ticker(
        symbol = code,
        name = name,
        marketCode = typeCode,
        marketName = typeName,
        nationCode = nationCode,
        nationName = nationName
    )
