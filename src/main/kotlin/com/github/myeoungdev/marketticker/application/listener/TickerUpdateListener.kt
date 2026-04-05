package com.github.myeoungdev.marketticker.application.listener

import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * 시세 갱신 이벤트를 브로드캐스트하는 메시지 버스 리스너입니다.
 */
interface TickerUpdateListener {
    companion object {
        val TOPIC = com.intellij.util.messages.Topic.create("Ticker Update", TickerUpdateListener::class.java)
    }
    fun onTickerUpdated(prices: List<TickerPrice>)
}
