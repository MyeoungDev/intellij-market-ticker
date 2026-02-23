package com.github.myeoungdev.marketticker.application.listener

import com.intellij.util.messages.Topic

fun interface WatchlistEntryUpdateListener {
    fun onWatchlistEntryUpdated()

    companion object {
        val TOPIC = Topic.create("Watchlist Entry Update", WatchlistEntryUpdateListener::class.java)
    }
}
