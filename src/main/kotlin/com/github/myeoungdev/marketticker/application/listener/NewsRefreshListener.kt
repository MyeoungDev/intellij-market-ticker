package com.github.myeoungdev.marketticker.application.listener

import com.intellij.util.messages.Topic

/**
 * 뉴스 탭을 명시적으로 다시 불러오라는 요청을 전달하는 리스너입니다.
 */
interface NewsRefreshListener {

    fun onNewsRefreshRequested()

    companion object {
        @JvmField
        val TOPIC: Topic<NewsRefreshListener> = Topic.create(
            "MarketTickerNewsRefreshRequested",
            NewsRefreshListener::class.java
        )
    }
}
