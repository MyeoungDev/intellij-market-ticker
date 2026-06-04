
# Market Ticker

<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Market Ticker Icon" width="88" height="88" />
</p>

<p align="center">
  <img alt="JetBrains Platform" src="https://img.shields.io/badge/JetBrains-2024.3+-000000?logo=intellijidea&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white">
  <img alt="JDK" src="https://img.shields.io/badge/JDK-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-Apache_2.0-4D7A97.svg">
  <img alt="Tests" src="https://img.shields.io/badge/Tests-Gradle%20test-success">
</p>

> Track Korean stocks, global stocks, and crypto from inside JetBrains IDEs.

Market Ticker is a JetBrains IDE plugin for developers who want a compact market dashboard without leaving the editor. It combines watchlists, overview, news, research, screener, calendar, alerts, chart, heatmap, and portfolio tracking in a single tool window.

## Preview

<img width="1510" height="898" alt="스크린샷 2026-06-04 오후 11 51 15" src="https://github.com/user-attachments/assets/a6552e21-8a73-4ca7-a819-7eaf2887ea0d" />

- Watchlist-first stock workspace inside the IDE
- Selector-based news categories optimized for narrow tool-window space
- Dedicated overview panel separated from news for faster reading
- Naver-backed screener presets and compact earnings / macro calendar

<!-- Plugin description -->
Market Ticker is an IntelliJ Platform plugin for tracking Korean stocks, global stocks, and crypto inside the IDE with watchlists, portfolio view, alerts, charts, screener, calendar, news, and research.

Some market, news, research, screener, and calendar data may be retrieved from third-party public web endpoints. Market Ticker is not affiliated with or endorsed by Naver, Finviz, Yahoo Finance, or any brokerage or exchange. Data availability and accuracy may change without notice.
<!-- Plugin description end -->

## Why This Exists

Most investor tools assume you are willing to context-switch into a browser, open multiple tabs, and keep a separate workflow for market monitoring.

Market Ticker takes the opposite approach:

- keep a watchlist visible while coding
- inspect market context quickly in a narrow tool window
- move between overview, news, research, and calendar without leaving the IDE
- stay lightweight enough for day-to-day use

## Features

- `🔎 Search`
  Fast symbol lookup across Korean stocks, global stocks, indexes, and crypto.

- `📌 Watchlist`
  Add, group, tag, edit, and remove tracked symbols without leaving the IDE.

- `⚡ Quotes`
  Real-time refresh with `AUTO / FIXED / MANUAL` modes.

- `🧾 Overview`
  Compact per-ticker summary optimized for small tool-window space.

- `📰 News`
  Selector-based Naver news categories instead of long stacked scrolling sections.

- `📚 Research`
  Core research, ranking research, and domestic stock research.

- `📈 Screener`
  Naver ranking-based discovery for search top, value top, rising, and falling names.

- `📅 Calendar`
  Earnings and economic event list with a detail panel.

- `💼 Portfolio`
  Market value, PnL, and return tracking.

- `🔔 Alerts`
  Target price and volatility alerts with repeat options.

- `🧩 Widget`
  Status bar ticker monitoring for passive market checks.

## Workflow

1. `Search`
   Search a symbol you care about from the stock tab.

2. `Add To Watchlist`
   Add the ticker and keep it visible while you code.

3. `Inspect Context`
   Read the overview, scan the latest news, check research, and glance at the chart.

4. `Discover More`
   Use the screener when you want ranked ideas, movers, or broader market discovery.

5. `Check Event Risk`
   Open the calendar to see earnings and macro events that may affect price action.

6. `Monitor Passively`
   Leave the tool window closed and keep an eye on prices from the status bar widget.

## Current Scope

Supported market scope:

- Korean stocks
- Global stocks
- Crypto
- Market indicators
- Naver-backed news and research

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

## License

[Apache-2.0 License](LICENSE)
