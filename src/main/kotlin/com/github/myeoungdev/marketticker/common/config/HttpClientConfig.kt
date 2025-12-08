package com.github.myeoungdev.marketticker.common.config

import java.net.http.HttpClient
import java.time.Duration

/**
 * 전역 HttpClient 싱글톤 인스턴스
 *
 * @author  : 강명관
 * @since   : 2025-12-05
 */
val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()