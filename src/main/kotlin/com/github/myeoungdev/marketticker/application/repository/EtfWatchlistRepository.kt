package com.github.myeoungdev.marketticker.application.repository

import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "EtfWatchlistRepository", storages = [Storage("market_ticker_etf_watchlist.xml")])
@Service(Service.Level.APP)
class EtfWatchlistRepository : PersistentStateComponent<EtfWatchlistRepository.State> {
    data class Entry(
        var code: String = "",
        var name: String = ""
    )

    data class State(var entries: MutableList<Entry> = mutableListOf())
    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = State(state.entries.map { it.copy() }.toMutableList()) }
    fun entries(): List<Entry> = state.entries.map { it.copy() }
    fun contains(code: String): Boolean = state.entries.any { it.code == code }
    fun add(etf: EtfSummary) { if (!contains(etf.code)) state.entries.add(Entry(etf.code, etf.name)) }
    fun remove(code: String) { state.entries.removeIf { it.code == code } }
}
