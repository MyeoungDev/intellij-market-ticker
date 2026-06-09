package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent

class PortfolioEditDialog(private val entry: WatchlistRepository.WatchlistEntry) : DialogWrapper(true) {

    private val localizationService = service<LocalizationService>()

    private val purchasePriceField = JBTextField()
    private val quantityField = JBTextField()

    var purchasePrice: Double? = null
        private set
    var quantity: Double? = null
        private set

    init {
        title = localizationService.text("${entry.name} 포트폴리오 편집", "${entry.name} Portfolio Edit")
        purchasePriceField.text = entry.purchasePrice?.toString() ?: ""
        quantityField.text = entry.quantity?.toString() ?: ""
        init()
        setSize(420, 180)
        isResizable = false
    }

    override fun createCenterPanel(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(420, 120)
            border = JBUI.Borders.empty(8, 0)
            add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent(JBLabel(localizationService.text("매수 단가", "Avg buy price")), purchasePriceField, 1, false)
                    .addVerticalGap(8)
                    .addLabeledComponent(JBLabel(localizationService.text("수량", "Quantity")), quantityField, 1, false)
                    .panel,
                BorderLayout.CENTER
            )
        }
    }

    override fun doOKAction() {
        purchasePrice = purchasePriceField.text.toDoubleOrNull()
        quantity = quantityField.text.toDoubleOrNull()
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return purchasePriceField
    }
}
