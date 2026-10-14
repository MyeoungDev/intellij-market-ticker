package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.model.theme.ThemeLoadState
import com.github.myeoungdev.marketticker.application.service.ThemeSummaryService
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 화면에 표시 중인 구성 종목만 관리하며, 공유 요청과 테마별 마지막 정상 데이터는 서비스가 보관합니다. */
internal class ThemeMemberInteraction(
    private val service: ThemeSummaryService,
    private val scope: CoroutineScope,
    private val uiDispatcher: CoroutineDispatcher,
    private val onChanged: () -> Unit
) : Disposable {
    var expandedThemeCode: String? = null
        private set
    var state = ThemeLoadState<List<ThemeConstituent>>()
        private set
    private var generation = 0L
    private var visible = false
    private var disposed = false
    private var request: Job? = null

    fun setVisible(value: Boolean) {
        if (disposed || visible == value) return
        visible = value
        invalidateRequest()
    }

    fun toggle(themeCode: String) {
        if (disposed || !visible) return
        invalidateRequest()
        expandedThemeCode = themeCode.takeUnless { it == expandedThemeCode }
        state = ThemeLoadState()
        if (expandedThemeCode == null) onChanged() else refresh()
    }

    fun refresh(force: Boolean = false) {
        if (disposed || !visible) return
        val themeCode = expandedThemeCode ?: return
        invalidateRequest()
        val requestId = generation
        val cached = service.memberState(themeCode)
        state = cached.copy(
            isLoading = force || cached.value == null || cached.isStale || cached.isLoading,
            isStale = cached.value != null && (force || cached.isStale || cached.isLoading)
        )
        onChanged()
        request = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                service.refreshMembers(themeCode, force)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Failures are rendered from the independent member entry, not summary status.
            }
            withContext(uiDispatcher) {
                if (disposed || !visible || requestId != generation || expandedThemeCode != themeCode) {
                    return@withContext
                }
                state = service.memberState(themeCode)
                onChanged()
            }
        }
    }

    private fun invalidateRequest() {
        ++generation
        request?.cancel()
        request = null
    }

    override fun dispose() {
        disposed = true
        visible = false
        invalidateRequest()
    }
}
