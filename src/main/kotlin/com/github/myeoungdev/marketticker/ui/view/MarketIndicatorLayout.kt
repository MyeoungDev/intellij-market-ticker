package com.github.myeoungdev.marketticker.ui.view

internal fun calculateIndicatorCardColumns(availableWidth: Int, itemCount: Int): Int {
    if (itemCount <= 1) return 1
    if (availableWidth <= 0) return 1

    val minCardWidth = 160
    val gap = 10
    val maxByWidth = ((availableWidth + gap) / (minCardWidth + gap)).coerceAtLeast(1)
    return minOf(itemCount, maxByWidth.coerceAtMost(4))
}
