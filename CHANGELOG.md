<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-market-ticker Changelog

## [Unreleased]

### Fixed

### Added

## [0.0.4] - 2026-06-15

### Fixed
- Fixed watchlist price flickering between NXT and KRX by keeping watchlist price rendering on the display-price update path. (#47)
- Fixed portfolio edit fields showing large saved numeric values in scientific notation. (#48)
- Fixed top-level tab order changing visually when the tool window is narrow. (#50)

### Added
- Added drag-and-drop reordering for watchlist rows when the group filter is set to All.(#43)
- Extended closed-market polling interval options up to 1 hour and changed the default closed-market interval to 5 minutes. (#45)
- Added a toggleable portfolio summary panel for holdings valuation, return, and daily change metrics. (#48)

## [0.0.3] - 2026-06-10

### Fixed
- Removed the group/tag field from portfolio registration and edit dialogs.
- Fixed polling interval changes not taking effect until the previous long delay completed.

### Added
- Added NXT venue support for domestic stocks, including KRX/NXT screener selection and configurable domestic stock venue display. (#35)
- Added longer fixed and open-market polling interval options up to 1 hour. (#36)
- Added a user-facing option to enable or disable automatic price polling while keeping manual refresh actions available.
- Added market-hours filtering for automatic price polling to skip closed regular stock markets while preserving manual refresh.
- Added an initial startup price refresh and watchlist market-session indicator so closed-market stocks still show their latest fetched price.
- Added a setting to show or hide the watchlist market-session indicator column.

## [0.0.2] - 2026-06-08
### Fixed
- Added portfolio item removal while keeping the watchlist entry. (#32)
- Fixed portfolio double-click selection to update overview, news, research, and chart panels.
- Fixed news category selection being reset after refresh.
- Fixed stale or missing chart rendering after ticker and period changes.
- Improved domestic screener fallback handling.

### Added
- Added GitHub issue templates.
- Added GitHub pull request template.

## [0.0.1] - 2026-06-05
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
