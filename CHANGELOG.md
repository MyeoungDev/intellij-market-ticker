<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-market-ticker Changelog

## [Unreleased]

### Fixed
- Removed the group/tag field from portfolio registration and edit dialogs.
- Fixed polling interval changes not taking effect until the previous long delay completed.

### Added
- Added NXT venue support for domestic stocks, including KRX/NXT screener selection and configurable domestic stock venue display. (#35)
- Added longer fixed and open-market polling interval options up to 1 hour. (#36)
- Added a user-facing option to enable or disable automatic price polling while keeping manual refresh actions available.

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
