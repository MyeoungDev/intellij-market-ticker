package com.github.myeoungdev.marketticker.startup

import com.github.myeoungdev.marketticker.application.service.MarketIndicatorService
import com.github.myeoungdev.marketticker.application.service.AppSettingsService
import com.github.myeoungdev.marketticker.application.service.MarketDataService
import com.github.myeoungdev.marketticker.application.service.PriceRefreshSource
import com.github.myeoungdev.marketticker.application.service.TickerSchedulerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * 프로젝트 시작 시 Market Ticker 핵심 서비스들을 초기화합니다.
 *
 * 앱 레벨 서비스는 lazy 생성이므로, 명시적으로 touch 하지 않으면
 * 폴링 스케줄러가 시작되지 않아 리프레시가 멈춘 상태가 될 수 있습니다.
 */
class MarketTickerStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        service<TickerSchedulerService>()
        service<MarketIndicatorService>()
        if (service<AppSettingsService>().isAutomaticPollingEnabled()) {
            service<MarketDataService>().refreshPricesAsync(PriceRefreshSource.STARTUP)
        }
    }
}
