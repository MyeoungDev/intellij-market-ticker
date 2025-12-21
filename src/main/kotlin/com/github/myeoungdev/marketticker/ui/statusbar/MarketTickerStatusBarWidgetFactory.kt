package com.github.myeoungdev.marketticker.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Some description here.
 *
 * @author : 강명관
 * @since : 1.0
 **/
class MarketTickerStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String {
        return MarketTickerStatusBarWidget.ID
    }

    override fun getDisplayName(): String = "Market Ticker"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return MarketTickerStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

}