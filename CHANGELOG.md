# Changelog

All notable changes to OctaReward Plus are documented here.

## [1.0.5-SNAPSHOT] - 2026-11-18

### Fixed
- Fixed Daily, Weekly, and Monthly category buttons so they open their reward claim pages instead of doing nothing.
- Removed the duplicate cooldown line from the Daily Reward category selector.
- Moved Supporter rewards into the Daily Reward selector as Premium Supporter rewards.
- Changed Supporter caches from one-time claims to independent 24-hour rolling cooldowns per configured rank reward.
- Added `luckperms-group` support with inherited LuckPerms group checks and the `octarewardplus.claim.support.<group>` permission route.
- Changed Daily Streak milestones to manual claims after the player's best streak reaches the corresponding day; previously reached milestones stay unlocked.
- Standardized the 100-day Streak and 100-hour Playtime boards to glass panes: green for claimed, yellow for available, and red for locked.
- Kept Playtime claims disabled until the player's live EssentialsX-compatible playtime meets the configured requirement.

### Changed
- Updated Maven and plugin metadata to `1.0.5-SNAPSHOT`.

## [1.0.4-SNAPSHOT] - 2026-11-14

### Added
- Declared EssentialsX as a mandatory runtime dependency; the playtime integration reads Bukkit player statistics directly.
- Added an EssentialsX-compatible playtime source that reads the same persisted Bukkit statistic as `/playtime`.
- Added regular and premium action lists for Daily, Weekly, and Monthly rewards, selected by configurable permissions.
- Added explicit 24-hour, 168-hour, and 720-hour default cooldowns for Daily, Weekly, and Monthly rewards.

### Changed
- Changed Daily, Weekly, and Monthly claims to use separate rolling cooldowns measured from each successful claim, following the independent cooldown model in the Athelion-Development DailyRewards reference.
- Set Playtime Reward defaults to one milestone per hour from 1 through 100 hours.
- Set Daily Streak defaults to one milestone for each consecutive day from day 1 through day 100.
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
