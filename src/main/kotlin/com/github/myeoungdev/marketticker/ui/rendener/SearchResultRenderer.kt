package com.github.myeoungdev.marketticker.ui.rendener

import com.github.myeoungdev.marketticker.domain.model.Ticker
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchItem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class SearchResultRenderer : ColoredListCellRenderer<Ticker>() {

    override fun customizeCellRenderer(
        list: JList<out Ticker>,
        value: Ticker?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        if (value == null) return

        // 1. 종목명 (굵게)
        append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)

        // 2. 공백
        append("  ")

        // 3. 코드 (회색)
        append(value.symbol, SimpleTextAttributes.GRAYED_ATTRIBUTES)

        // 4. 시장 정보 (작게, 괄호)
        append(" (${value.symbol})", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
    }
}