package com.github.myeoungdev.intellijmarketticker.infrastructure.naver

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class NaverSearchResultPayload(
    val query: String,
    val items: List<NaverSearchItem>
)