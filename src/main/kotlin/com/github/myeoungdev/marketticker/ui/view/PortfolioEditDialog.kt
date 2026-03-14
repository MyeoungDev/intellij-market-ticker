package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.repository.WatchlistRepository
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class PortfolioEditDialog(private val entry: WatchlistRepository.WatchlistEntry) : DialogWrapper(true) {

    private val localizationService = service<LocalizationService>()

    private val purchasePriceField = JBTextField()
    private val quantityField = JBTextField()
    private val groupTagField = JBTextField()

    var purchasePrice: Double? = null
        private set
    var quantity: Double? = null
        private set
    var groupTag: String = ""
        private set

    init {
        title = localizationService.text("${entry.name} 포트폴리오 편집", "${entry.name} Portfolio Edit")
        purchasePriceField.text = entry.purchasePrice?.toString() ?: ""
        quantityField.text = entry.quantity?.toString() ?: ""
        groupTagField.text = entry.groupTag
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(localizationService.text("매수 단가", "Avg buy price")), purchasePriceField, 1, false)
            .addLabeledComponent(JBLabel(localizationService.text("수량", "Quantity")), quantityField, 1, false)
            .addLabeledComponent(JBLabel(localizationService.text("그룹/태그", "Group/Tag")), groupTagField, 1, false)
            .panel
    }

    override fun doOKAction() {
        purchasePrice = purchasePriceField.text.toDoubleOrNull()
        quantity = quantityField.text.toDoubleOrNull()
        groupTag = groupTagField.text.trim()
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return purchasePriceField
    }
}
