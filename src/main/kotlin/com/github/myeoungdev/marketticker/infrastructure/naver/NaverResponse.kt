package com.github.myeoungdev.marketticker.infrastructure.naver

/**
 * Naver 에서 사용되는 공통 응답 Wrapper
 *
 * @author  : 강명관
 * @since   : 2025-11-30
 */
data class NaverResponse<T>(
    val isSuccess: Boolean,
    val detailCode: String?,
    val message: String?,
    val result: T?
)