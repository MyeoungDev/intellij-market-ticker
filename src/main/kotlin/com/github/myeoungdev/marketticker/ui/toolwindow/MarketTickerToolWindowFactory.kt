package com.github.myeoungdev.marketticker.ui.toolwindow

import com.github.myeoungdev.marketticker.ui.view.MarketTickerView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Market Ticker 툴윈도우 내용을 생성합니다.
 */
class MarketTickerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val view = MarketTickerView(project)
        val content = contentFactory.createContent(view.mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
