package com.github.myeoungdev.marketticker.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarketTickerToolWindowFactoryTest {

    @Test
    fun `툴윈도우는 인덱싱 중에도 생성 가능하다`() {
        assertThat(MarketTickerToolWindowFactory()).isInstanceOf(DumbAware::class.java)
    }
}
