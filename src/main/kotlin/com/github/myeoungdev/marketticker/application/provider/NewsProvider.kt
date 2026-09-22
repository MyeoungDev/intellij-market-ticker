package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.news.HeadlineNewsBundle
import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryLoadState
import com.github.myeoungdev.marketticker.domain.model.news.NewsCategoryPage
import com.github.myeoungdev.marketticker.domain.model.news.NewsLoadStatus
import com.github.myeoungdev.marketticker.domain.model.news.TickerNewsBundle
import com.github.myeoungdev.marketticker.domain.model.Ticker

/**
 * 뉴스 데이터를 공급하는 provider 인터페이스입니다.
 */
interface NewsProvider {

    fun getHeadlineNews(pageSize: Int = 15): HeadlineNewsBundle

    fun getMostViewedNews(limit: Int = 15): List<NewsArticle>

    fun getMostViewedNewsPage(limit: Int = 15): NewsCategoryPage {
        val articles = getMostViewedNews(limit)
        return NewsCategoryPage(
            articles = articles,
            state = NewsCategoryLoadState(if (articles.isEmpty()) NewsLoadStatus.EMPTY else NewsLoadStatus.SUCCESS)
        )
    }

    fun getTickerNews(ticker: Ticker): TickerNewsBundle

    fun getCategoryNews(categoryKey: String, page: Int = 1, pageSize: Int = 15): List<NewsArticle>

    fun getCategoryNewsPage(categoryKey: String, page: Int = 1, pageSize: Int = 15): NewsCategoryPage {
        val articles = getCategoryNews(categoryKey, page, pageSize)
        return NewsCategoryPage(
            articles = articles,
            state = NewsCategoryLoadState(if (articles.isEmpty()) NewsLoadStatus.EMPTY else NewsLoadStatus.SUCCESS)
        )
    }

    fun searchNews(query: String, page: Int = 1, pageSize: Int = 7): List<NewsArticle>
}
