# Changelog

All notable changes to OctaReward Plus are documented here.

## [1.0.4-SNAPSHOT] - 2026-11-14

### Added
- Declared EssentialsX as a mandatory runtime dependency and added its Maven API dependency with `provided` scope.
- Added an EssentialsX-compatible playtime source that reads the same persisted Bukkit statistic as `/playtime`.
- Added regular and premium action lists for Daily, Weekly, and Monthly rewards, selected by configurable permissions.
- Added explicit 24-hour, 168-hour, and 720-hour default cooldowns for Daily, Weekly, and Monthly rewards.

### Changed
- Changed Daily, Weekly, and Monthly claims to use separate rolling cooldowns measured from each successful claim, following the independent cooldown model in the Athelion-Development DailyRewards reference.
- Set Playtime Reward defaults to one milestone per hour from 1 through 100 hours.
- Set Daily Streak defaults to one milestone for each consecutive day from day 1 through day 100; each reached day is rewarded automatically by a successful daily claim.
- Updated GUI cooldown displays and PlaceholderAPI status values to use the active cooldown timestamps.
- Updated the README, Maven metadata, and plugin description for version 1.0.4-SNAPSHOT.

### Performance
- Removed redundant cooldown database lookups in the periodic reward GUI.
- Aligned playtime snapshot synchronization with `playtime.sync-interval-seconds`.
- Retained bounded asynchronous database work and bounded player-state caches.

## [1.0.3-SNAPSHOT] - 2026-11-14

### Added
- Rebuilt the GUI into four top-level sections: Basic Reward, Daily Streak, Playtime Reward, and Extra Reward.
- Added Daily Reward (Daily / Weekly / Monthly) and configurable Supporter Rank Reward catalog.
- Added LuckPerms-compatible permission checks for supporter rank rewards.
- Added 100 hourly playtime milestones and 100 daily streak milestones.
- Added reset countdown lore for daily, weekly, and monthly rewards.

### Changed
- Standardized all menus to six rows (54 slots).
- Disabled default italic decoration on plugin-generated item names and lore.
- Updated glass-pane decoration to a dark jewel palette.

## [1.0.2-SNAPSHOT] - 2026-11-14

### Added
- Added separate Daily, Weekly, and Monthly reward categories.
- Added winding progression boards for playtime and streak rewards.
- Added seasonal event schedules and additional reward placeholders.

## [0.0.1-SNAPSHOT] - 2026-11-14

### Added
- Added the initial Maven project structure, Java 21 compiler configuration, unit tests, and GitHub Actions workflow.
- Added SQLite/MySQL storage, reward actions, daily claims, streak milestones, playtime progression, custom events, one-time rewards, PlaceholderAPI, and optional Vault integration.
