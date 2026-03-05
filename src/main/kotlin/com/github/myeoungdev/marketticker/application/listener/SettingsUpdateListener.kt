package com.github.myeoungdev.marketticker.application.listener

import com.intellij.util.messages.Topic

/**
 * 설정 변경 시 UI 갱신을 위한 애플리케이션 메시지 버스 리스너입니다.
 */
interface SettingsUpdateListener {

    fun onSettingsUpdated()

    companion object {
        @JvmField
        val TOPIC: Topic<SettingsUpdateListener> = Topic.create(
            "MarketTickerSettingsUpdated",
            SettingsUpdateListener::class.java
        )
    }
}
