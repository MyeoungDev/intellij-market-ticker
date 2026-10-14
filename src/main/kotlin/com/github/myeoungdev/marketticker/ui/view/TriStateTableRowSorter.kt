package com.github.myeoungdev.marketticker.ui.view

import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

internal class TriStateTableRowSorter<M : TableModel>(model: M) : TableRowSorter<M>(model) {
    override fun toggleSortOrder(column: Int) {
        val primaryKey = sortKeys.firstOrNull()
        if (primaryKey?.column == column && primaryKey.sortOrder == SortOrder.DESCENDING) {
            sortKeys = emptyList<RowSorter.SortKey>()
            return
        }
        super.toggleSortOrder(column)
    }
}
