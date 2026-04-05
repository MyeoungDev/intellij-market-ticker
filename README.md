# Market Ticker

Market Ticker is a JetBrains IDE plugin for tracking Korean stocks, global stocks, and crypto inside the IDE.

It focuses on a compact, watchlist-first workflow with quotes, overview, news, research, screener, calendar, alerts, charts, heatmap, and portfolio tracking in one tool window.

<!-- Plugin description -->
Market Ticker is an IntelliJ Platform plugin for tracking Korean stocks, global stocks, and crypto inside the IDE with watchlists, portfolio view, alerts, charts, screener, calendar, news, and research.

Some market, news, research, screener, and calendar data may be retrieved from third-party public web endpoints. Market Ticker is not affiliated with or endorsed by Naver, Finviz, Yahoo Finance, or any brokerage or exchange. Data availability and accuracy may change without notice.
<!-- Plugin description end -->

## Features

- Real-time Market Tracking
  - Track Korean stocks, global stocks, indexes, and crypto from inside the IDE
- Watchlist-first Workflow
  - Search, add, group, and monitor symbols without leaving the editor
- Compact Stock Workspace
  - `Overview / News / Research / Chart / Heatmap` panels optimized for small tool-window space
- News And Research
  - Naver-backed selector-based news categories and research views
- Screener
  - Naver ranking-based discovery for search top, value top, rising, and falling names
- Calendar
  - Earnings and economic event list with a detail panel
- Portfolio And Alerts
  - Track PnL / return and configure target price or volatility alerts
- Status Bar Widget
  - Passive price monitoring without opening the tool window

## Supported Scope

- Korean stocks
- Global stocks
- Crypto
- Market indicators
- Naver-backed news and research

## Data Sources

- Quotes / news / research / domestic overview / screener
  - Naver public web endpoints
- Calendar supplemental data
  - Finviz public web endpoints
- Some auxiliary data
  - other third-party public web endpoints

## Quick Start

1. Open the project in IntelliJ IDEA 2024.3+
2. Use JDK 21
3. Run the sandbox IDE:

```bash
./gradlew runIde
```

4. Open the `Market Ticker` tool window
5. Search a symbol and add it to the watchlist

## Development

Requirements:
- JDK 21
- IntelliJ IDEA 2024.3+
- macOS / Linux / Windows

SDKMAN example:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.9-tem
./gradlew test
```

Run tests:

```bash
./gradlew test
```

Current local verification:
- Java 21
- `BUILD SUCCESSFUL`

## Tech Stack

- Kotlin
- Coroutines / StateFlow
- IntelliJ Platform SDK
- Gradle
- Jackson
- Java HttpClient
- JUnit 5
- AssertJ
- WireMock

## Architecture

```text
src/main/kotlin/com/github/myeoungdev/marketticker
├── application      # services, listeners, repositories, provider ports
├── domain           # core domain models
├── infrastructure   # external integrations (Naver / Finviz supplemental)
└── ui               # tool window, dialogs, status bar, views
```

## UI Layout

- Top tabs
  - `주식 / 스크리너 / 캘린더 / 뉴스 / 리서치`
- Stock tab
  - Search results
  - Watchlist / portfolio
  - Bottom panels: `개요 / 뉴스 / 리서치 / 차트 / 히트맵`
- News tab
  - Category selector
  - Most viewed tab
  - Detail panel
- Research tab
  - Core research
  - Ranking research
  - Domestic stock research

## Persistent State

The plugin stores state in the IDE config directory:

- `market_ticker_watchlist.xml`
- `market_ticker_alerts.xml`
- `market_ticker_settings.xml`

## External Data Notice

- This plugin may retrieve some market, news, research, screener, and calendar data from third-party public web endpoints.
- Market Ticker is not affiliated with or endorsed by Naver, Finviz, Yahoo Finance, or any brokerage or exchange.
- Data availability, response shape, latency, and accuracy may change without notice.
- The plugin is intended for monitoring and information use, not order execution.
- Users remain responsible for investment decisions.

## Release Checklist

- Review `plugin.xml` name / description / vendor
- Configure signing and publishing secrets
- Run tests
- Run Plugin Verifier
- Re-check README and Marketplace copy

## License

Apache-2.0
