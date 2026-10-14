package com.github.myeoungdev.marketticker.application.service

import com.github.myeoungdev.marketticker.application.model.theme.ThemeLoadState
import com.github.myeoungdev.marketticker.application.model.theme.ThemeManualRefreshState
import com.github.myeoungdev.marketticker.application.model.theme.ThemeRefreshThrottledException
import com.github.myeoungdev.marketticker.application.provider.DefaultDataSourceRegistry
import com.github.myeoungdev.marketticker.application.provider.ThemeSummaryProvider
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeConstituent
import com.github.myeoungdev.marketticker.domain.model.theme.ThemeSummarySnapshot
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service(Service.Level.APP)
class ThemeSummaryService(
    private val provider: ThemeSummaryProvider = DefaultDataSourceRegistry.themeSummaryProvider(),
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : Disposable {
    private class Entry<T : Any> {
        var value: T? = null
        var fetchedAt: Instant? = null
        var error: Throwable? = null
        var inFlight: CompletableDeferred<T>? = null
    }

    private val lock = Any()
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())
    private val summary = Entry<ThemeSummarySnapshot>()
    private val members = mutableMapOf<String, Entry<List<ThemeConstituent>>>()
    private var disposed = false
    private var lastManualRefresh: Instant? = null

    fun summaryState(): ThemeLoadState<ThemeSummarySnapshot> = synchronized(lock) { state(summary) }

    fun memberState(themeCode: String): ThemeLoadState<List<ThemeConstituent>> = synchronized(lock) {
        members[themeCode]?.let { state(it) } ?: ThemeLoadState()
    }

    fun manualRefreshState(): ThemeManualRefreshState = synchronized(lock) {
        val remaining = manualCooldownRemaining()
        ThemeManualRefreshState(!disposed && summary.inFlight == null && remaining.isZero, remaining)
    }

    suspend fun refresh(force: Boolean = false): ThemeSummarySnapshot {
        currentCoroutineContext().ensureActive()
        return synchronized(lock) { request(summary, force, provider::getDailySummary) }.await()
    }

    /** 스크리너의 테마 화면이 보일 때만 조회하며, 별도 폴링 작업은 시작하지 않습니다. */
    suspend fun refreshIfStale(isVisible: Boolean): ThemeSummarySnapshot? {
        currentCoroutineContext().ensureActive()
        return if (isVisible) refresh() else summaryState().value
    }

    /** 진행 중인 조회가 있으면 합류하고, 없으면 수동 갱신 제한을 확인한 뒤 새로 조회합니다. */
    suspend fun refreshManually(): ThemeSummarySnapshot {
        currentCoroutineContext().ensureActive()
        val result = synchronized(lock) {
            checkActive()
            summary.inFlight ?: run {
                val remaining = manualCooldownRemaining()
                if (!remaining.isZero) throw ThemeRefreshThrottledException(remaining)
                lastManualRefresh = clock.instant()
                request(summary, true, provider::getDailySummary)
            }
        }
        return result.await()
    }

    suspend fun refreshMembers(themeCode: String, force: Boolean = false): List<ThemeConstituent> {
        require(themeCode.isNotBlank()) { "Theme code is required" }
        currentCoroutineContext().ensureActive()
        return synchronized(lock) {
            checkActive()
            request(members.getOrPut(themeCode) { Entry() }, force) { provider.getThemeMembers(themeCode) }
        }.await()
    }

    // All entry mutations, including completion, are serialized with synchronous disposal.
    private fun <T : Any> request(entry: Entry<T>, force: Boolean, loader: () -> T): Deferred<T> {
        checkActive()
        entry.inFlight?.let { return it }
        entry.value?.let { if (!force && !state(entry).isStale) return CompletableDeferred(it) }

        // No caller parent: cancelling an awaiter must not cancel another consumer's shared operation.
        val result = CompletableDeferred<T>()
        entry.inFlight = result
        val worker = scope.launch {
            try {
                val value = runInterruptible(ioDispatcher, loader)
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    checkActive()
                    if (entry.inFlight === result) {
                        entry.value = value
                        entry.fetchedAt = clock.instant()
                        entry.error = null
                        entry.inFlight = null
                        result.complete(value)
                    }
                }
            } catch (failure: Throwable) {
                synchronized(lock) {
                    if (entry.inFlight === result) {
                        entry.error = failure
                        entry.inFlight = null
                    }
                    result.completeExceptionally(failure)
                }
            }
        }
        // A coroutine cancelled before its body starts never enters try/catch.
        worker.invokeOnCompletion { failure ->
            synchronized(lock) {
                if (entry.inFlight === result) {
                    entry.inFlight = null
                    entry.error = failure ?: CancellationException("Theme worker did not complete")
                }
                if (!result.isCompleted) {
                    result.completeExceptionally(failure ?: CancellationException("Theme worker did not complete"))
                }
            }
        }
        return result
    }

    private fun <T : Any> state(entry: Entry<T>): ThemeLoadState<T> {
        val expired = entry.fetchedAt?.let { !clock.instant().isBefore(it.plus(TTL)) } ?: true
        return ThemeLoadState(entry.value, entry.fetchedAt, entry.inFlight != null,
            entry.value != null && (expired || entry.inFlight != null || entry.error != null), entry.error)
    }

    private fun manualCooldownRemaining(): Duration = lastManualRefresh?.let {
        maxOf(Duration.ZERO, Duration.between(clock.instant(), it.plus(MANUAL_COOLDOWN)))
    } ?: Duration.ZERO

    private fun checkActive() {
        if (disposed) throw CancellationException("Theme summary service disposed")
    }

    override fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            val cancellation = CancellationException("Theme summary service disposed")
            fun <T : Any> cancel(entry: Entry<T>) {
                entry.inFlight?.let {
                    entry.error = cancellation
                    it.cancel(cancellation)
                }
                entry.inFlight = null
            }
            cancel(summary)
            members.values.forEach { cancel(it) }
        }
        scope.cancel()
    }

    companion object {
        private val TTL = Duration.ofMinutes(5)
        private val MANUAL_COOLDOWN = Duration.ofSeconds(30)
    }
}
