package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.domain.model.MarketType
import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import javax.swing.JPanel
import javax.swing.JTabbedPane

class ScreenerTickerSelectionTest {
    @Test
    fun `selection shows existing details once and activates Stocks by component identity`() {
        val stocks = JPanel()
        val screenerModes = JTabbedPane().apply {
            addTab("Rankings", JPanel())
            addTab("Themes", JPanel())
            selectedIndex = 1
        }
        val screener = TabInfo(screenerModes)
        val stocksTab = TabInfo(stocks)
        val misleadingTab = TabInfo(JPanel())
        val events = mutableListOf<String>()
        var selectedTab = screener
        val tabs = Proxy.newProxyInstance(JBTabs::class.java.classLoader, arrayOf(JBTabs::class.java)) { _, method, args ->
            when (method.name) {
                "getTabs" -> listOf(misleadingTab, screener, stocksTab)
                "select" -> {
                    selectedTab = args!![0] as TabInfo
                    assertThat(args[1]).isEqualTo(true)
                    events.add("activate")
                    ActionCallback.DONE
                }
                else -> error("Unexpected tab operation: ${method.name}")
            }
        } as JBTabs
        val ticker = Ticker("005930", "005930", "Samsung", MarketType.KOSPI, "KOR", "Korea")
        val details = mutableListOf<Ticker>()
        val callback = screenerTickerSelection(tabs, stocks) {
            events.add("details")
            details.add(it)
        }

        callback(ticker)

        assertThat(details).containsExactly(ticker)
        assertThat(events).containsExactly("details", "activate")
        assertThat(selectedTab).isSameAs(stocksTab)
        selectedTab = screener
        assertThat(selectedTab.component).isSameAs(screenerModes)
        assertThat(screenerModes.selectedIndex).isEqualTo(1)
    }
}
