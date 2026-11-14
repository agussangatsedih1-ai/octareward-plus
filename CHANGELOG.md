# Changelog

All notable changes to OctaReward Plus are documented here.

## [1.0.2-SNAPSHOT] - 2026-11-14

### Added
- Added independent Daily, Weekly, and Monthly reward categories with distinct claim periods.
- Added a five-row serpentine progression board for Playtime Rewards and Daily Streak milestones.
- Added status-specific GUI icons for claimed and locked rewards, glow effects for notable states, decorative lore, and progress bars.
- Added configurable seasonal schedules for Winter, Christmas, Spring, Easter, and other custom events.
- Added automatic global event-instance scheduling through `availability.auto-start` and ISO-8601 start/end timestamps.
- Added placeholders for weekly/monthly status, claim status, reset times, streak milestones, event availability, and per-reward state/claim/availability status.
- Added the `/daily`, `/reward`, `/dailyrewards`, and `/rewards` aliases while retaining legacy aliases.
- Added separate `weekly.yml`, `monthly.yml`, and `seasonal-events.yml` reward files.

### Changed
- Replaced the placeholder plugin description with a descriptive project summary.
- Updated the project version to `1.0.2-SNAPSHOT`.
- Reworked the main menu and category menus with a more decorative visual language.
- Merged newly added GUI defaults into an existing `gui.yml` without overwriting configured values.
- Kept seasonal event schedules separate from existing custom event definitions to avoid replacing existing server configuration.
- Updated the README and examples in English.

### Performance and maintenance
- Kept database access asynchronous and separated inventory rendering onto the Bukkit main thread.
- Kept online playtime activity maps cleared when a player quits and bounded cached offline player snapshots.
- Invalidated per-player PlaceholderAPI cache entries on logout and unregistered optional expansions on shutdown.
- Retained a bounded database worker pool and pooled SQL connections.
- Preserved the existing claim ledger and unique database constraints for duplicate-claim protection.

### Compatibility notes
- Paper 1.21.11 and Java 21 are the intended target.
- PlaceholderAPI and Vault remain optional integrations.
- The UltimateRewards integration remains runtime-reflection based.
- The Maven build must pass in GitHub Actions or a Maven-enabled environment before the shaded JAR is considered verified.

## [0.0.1-SNAPSHOT] - 2026-11-14

### Added
- Added the initial Maven project structure, Java 21 compiler configuration, unit tests, and GitHub Actions workflow.
- Added SQLite/MySQL storage, reward actions, daily claims, streak milestones, playtime progression, custom events, one-time rewards, PlaceholderAPI, and optional Vault integration.

### Fixed
- Corrected the Bukkit `EventExecutor` import in the optional UltimateRewards bridge.
