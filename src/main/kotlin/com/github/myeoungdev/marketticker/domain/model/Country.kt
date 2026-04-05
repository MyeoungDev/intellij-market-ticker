package com.github.myeoungdev.marketticker.domain.model

/**
 * 종목의 대표 국가를 표현하는 도메인 모델입니다.
 */
enum class Country(
    val code: String,
    val displayName: String
) {
    KOREA("KR", "대한민국"),
    USA("US", "미국"),
    JAPAN("JP", "일본"),
    UNKNOWN("XX", "기타")
}
