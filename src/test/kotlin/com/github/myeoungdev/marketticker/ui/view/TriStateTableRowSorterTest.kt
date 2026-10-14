package com.github.myeoungdev.marketticker.ui.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.SortOrder
import javax.swing.table.DefaultTableModel

class TriStateTableRowSorterTest {

    @Test
    fun `header clicks cycle ascending descending unsorted`() {
        val model = DefaultTableModel(arrayOf(arrayOf("B"), arrayOf("A")), arrayOf("name"))
        val sorter = TriStateTableRowSorter(model)

        sorter.toggleSortOrder(0)
        assertEquals(listOf(SortOrder.ASCENDING), sorter.sortKeys.map { it.sortOrder })

        sorter.toggleSortOrder(0)
        assertEquals(listOf(SortOrder.DESCENDING), sorter.sortKeys.map { it.sortOrder })

        sorter.toggleSortOrder(0)
        assertEquals(emptyList<SortOrder>(), sorter.sortKeys.map { it.sortOrder })
    }
}
