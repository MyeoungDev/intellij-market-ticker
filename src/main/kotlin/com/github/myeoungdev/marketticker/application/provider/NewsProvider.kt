package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.TickerNewsBundle
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * 뉴스 데이터를 공급하는 provider 인터페이스입니다.
 */
interface NewsProvider {

    fun getHeadlineNews(pageSize: Int = 15): HeadlineNewsBundle

    fun getMostViewedNews(limit: Int = 15): List<NewsArticle>

    fun getTickerNews(ticker: Ticker): TickerNewsBundle

    fun getCategoryNews(categoryKey: String, page: Int = 1, pageSize: Int = 15): List<NewsArticle>

    fun searchNews(query: String, page: Int = 1, pageSize: Int = 7): List<NewsArticle>
}
