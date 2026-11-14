# Changelog

All notable changes to OctaReward Plus are documented in this file.

## [0.0.1-SNAPSHOT] - 2026-11-14

### Added
- Added a Maven build using `pom.xml` and Java 21 compiler settings.
- Added Maven Shade packaging for HikariCP, SQLite JDBC, and MySQL Connector/J.
- Added optional PlaceholderAPI integration with the `octarewardplus` namespace and legacy `dailyreward` aliases.
- Added optional Vault Economy integration, including the `[money] <amount>` reward action and balance placeholders.
- Added the requested `Lore:` YAML block-list format while retaining lowercase `lore` compatibility.

### Changed
- Rebranded the plugin to **OctaReward Plus** with Bukkit/Paper plugin ID `OctaRewardPlus`.
- Set the project version to `0.0.1-SNAPSHOT`.
- Set the author to `Alvran`.
- Set the plugin description to `Suprise me please`.
- Updated the project documentation to English and changed the build instructions to Maven.
- Added `/orp` and `/octarewardplus` command aliases while retaining the existing `/dailyreward` command and permission nodes.

### Notes
- Paper API, PlaceholderAPI, and VaultAPI are provided dependencies and are not included in the plugin JAR.
- UltimateRewards remains an optional runtime integration; its API compatibility must be checked against the exact server-side JAR.
- A successful Maven build has not yet been verified in the preparation environment because Maven is unavailable there.
