package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle

internal data class NewsPageUpdate(
    val mergedArticles: List<NewsArticle>,
    val appendedArticles: List<NewsArticle>,
    val shouldAdvancePage: Boolean,
    val hasMore: Boolean
)

internal object NewsPagingPolicy {
    fun merge(existingArticles: List<NewsArticle>, incomingArticles: List<NewsArticle>, pageSize: Int): NewsPageUpdate {
        val existingKeys = existingArticles.map { articleIdentity(it) }.toHashSet()
        val appendedArticles = incomingArticles.filter { articleIdentity(it) !in existingKeys }
        return NewsPageUpdate(
            mergedArticles = existingArticles + appendedArticles,
            appendedArticles = appendedArticles,
            shouldAdvancePage = incomingArticles.isNotEmpty(),
            hasMore = incomingArticles.size >= pageSize
        )
    }

    private fun articleIdentity(article: NewsArticle): String {
        return listOfNotNull(
            article.url?.takeIf { it.isNotBlank() },
            article.title.takeIf { it.isNotBlank() }
        ).joinToString("::")
    }
}
