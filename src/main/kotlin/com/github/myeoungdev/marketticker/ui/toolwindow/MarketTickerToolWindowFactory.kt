package com.github.myeoungdev.marketticker.ui.toolwindow

import com.github.myeoungdev.marketticker.ui.view.MarketTickerView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class MarketTickerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val view = MarketTickerView(project)
        val content = contentFactory.createContent(view.mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

