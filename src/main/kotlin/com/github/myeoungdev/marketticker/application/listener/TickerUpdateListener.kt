package com.github.myeoungdev.marketticker.application.listener

import com.github.myeoungdev.marketticker.domain.model.TickerPrice

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-26
 */
interface TickerUpdateListener {
    companion object {
        val TOPIC = com.intellij.util.messages.Topic.create("Ticker Update", TickerUpdateListener::class.java)
    }
    fun onTickerUpdated(prices: List<TickerPrice>)
}