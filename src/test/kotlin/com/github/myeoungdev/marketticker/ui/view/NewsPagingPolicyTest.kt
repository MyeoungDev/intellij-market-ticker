package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.news.NewsArticle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NewsPagingPolicyTest {

    @Test
    fun `중복만 있는 꽉 찬 페이지도 다음 페이지가 있을 수 있다고 유지한다`() {
        val existing = listOf(sampleArticle("a", "같은 기사"))
        val incoming = List(15) { sampleArticle("a", "같은 기사") }

        val result = NewsPagingPolicy.merge(existing, incoming, pageSize = 15)

        assertThat(result.appendedArticles).isEmpty()
        assertThat(result.mergedArticles).hasSize(1)
        assertThat(result.shouldAdvancePage).isTrue()
        assertThat(result.hasMore).isTrue()
    }

    @Test
    fun `빈 페이지는 더 이상 불러올 수 없다고 종료한다`() {
        val result = NewsPagingPolicy.merge(
            existingArticles = listOf(sampleArticle("a", "기존 기사")),
            incomingArticles = emptyList(),
            pageSize = 15
        )

        assertThat(result.appendedArticles).isEmpty()
        assertThat(result.mergedArticles).hasSize(1)
        assertThat(result.shouldAdvancePage).isFalse()
        assertThat(result.hasMore).isFalse()
    }

    @Test
    fun `새 기사와 함께 온 부분 페이지는 page cursor를 전진하고 종료로 판정한다`() {
        val existing = listOf(sampleArticle("a", "기존 기사"))
        val incoming = listOf(
            sampleArticle("a", "기존 기사"),
            sampleArticle("b", "새 기사")
        )

        val result = NewsPagingPolicy.merge(existing, incoming, pageSize = 15)

        assertThat(result.appendedArticles).containsExactly(sampleArticle("b", "새 기사"))
        assertThat(result.mergedArticles).hasSize(2)
        assertThat(result.shouldAdvancePage).isTrue()
        assertThat(result.hasMore).isFalse()
    }

    private fun sampleArticle(id: String, title: String): NewsArticle {
        return NewsArticle(
            id = id,
            title = title,
            summary = "summary",
            source = "source",
            publishedAt = "2026-03-09 22:30",
            url = "https://example.com/$id"
        )
    }
}
