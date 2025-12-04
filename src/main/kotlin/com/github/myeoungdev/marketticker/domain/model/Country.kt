package com.github.myeoungdev.marketticker.domain.model

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-04
 */
enum class Country(
    val code: String,
    val displayName: String
) {
    KOREA("KR", "대한민국"),
    USA("US", "미국"),
    UNKNOWN("XX", "기타")
}