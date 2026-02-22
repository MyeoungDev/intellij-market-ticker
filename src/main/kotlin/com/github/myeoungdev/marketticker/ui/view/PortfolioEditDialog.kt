package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class PortfolioEditDialog(private val entry: WatchlistRepository.WatchlistEntry) :
    DialogWrapper(true) { // Changed parameter

    private val purchasePriceField = JBTextField()
    private val quantityField = JBTextField()

    var purchasePrice: Double? = null
        private set
    var quantity: Double? = null
        private set

    init {
        title = "${entry.name} 포트폴리오 정보 편집" // Updated to use entry.name
        purchasePriceField.text = entry.purchasePrice?.toString() ?: "" // Updated to use entry.purchasePrice
        quantityField.text = entry.quantity?.toString() ?: "" // Updated to use entry.quantity
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("매수 단가:"), purchasePriceField, 1, false)
            .addLabeledComponent(JBLabel("수량:"), quantityField, 1, false)
            .panel
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