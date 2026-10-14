package com.github.myeoungdev.marketticker.ui.view

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThemeSummaryLayoutTest {

    @Test
    fun `theme sections stack below 640 pixels and use two columns from 640`() {
        assertThat(themeSummaryColumnCount(360)).isEqualTo(1)
        assertThat(themeSummaryColumnCount(639)).isEqualTo(1)
        assertThat(themeSummaryColumnCount(640)).isEqualTo(2)
        assertThat(themeSummaryColumnCount(1_024)).isEqualTo(2)
    }
}
