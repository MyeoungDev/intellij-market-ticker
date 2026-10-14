package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot

interface ThemeSummaryProvider {
    /** 블로킹·중단 가능한 호출이며, 실패는 빈 결과로 바꾸지 않고 전달합니다. */
    fun getDailySummary(): ThemeSummarySnapshot

    /** 공급자 거래량순 구성 종목을 최대 20개 반환합니다. */
    fun getThemeMembers(themeCode: String): List<ThemeConstituent>
}
