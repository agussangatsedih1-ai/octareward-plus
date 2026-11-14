# OctaReward Plus

A configurable reward progression suite for Paper servers. Build daily habits, complete weekly and monthly reward cycles, follow winding playtime and streak trails, and celebrate seasonal events throughout the year.

## Project metadata

- **Plugin ID:** `OctaRewardPlus`
- **Version:** `1.0.3-SNAPSHOT`
- **Author:** `Alvran`
- **Description:** Turn every return into an adventure with daily treasures, weekly and monthly milestones, seasonal celebrations, and winding playtime and streak journeys.
- **Target:** Paper 1.21.11 / Java 21

## Features

- **Daily, Weekly, and Monthly Rewards** — independent claim ledgers and period keys. Daily periods follow the configured reset time, weekly periods follow ISO weeks (Monday start), and monthly periods reset on the first day of each month.
- **Daily Streak** — consecutive daily claims increase the streak once per daily period. `RESET`, `PAUSE`, and `GRACE_PERIOD` policies are supported, with configurable milestones.
- **Playtime Progression** — threshold and repeatable rewards, optional AFK filtering, excluded worlds, periodic persistence, and a five-row serpentine progression board. Reward claims are validated again by the service and database.
- **Serpentine Streak Trail** — milestone rewards are displayed along the same winding 5 x 9 progression track. Streak milestones are delivered automatically when the matching daily streak milestone is reached.
- **Seasonal Events** — configure Winter, Christmas, Spring, Easter, or custom celebration events with start/end timestamps. Set `availability.auto-start: true` to schedule a global event instance directly from YAML. Existing manual and activity-triggered events remain available.
- **One-Time Rewards** — one claim per UUID/reward ID, with optional permission, first-join condition, and start/end timestamps.
- **Fancy inventory menus** — status-specific icons, glowing ready/claimed items, decorative lore, progress bars, and a serpentine playtime/streak route.
- **Optional integrations** — PlaceholderAPI, Vault Economy, and the runtime-only UltimateRewards bridge.
- **Storage** — SQLite by default or MySQL/MariaDB for shared data. Unique claim keys protect against duplicate claims.

Reward actions support console/player commands (player commands are opt-in), messages, broadcasts, titles, action bars, sounds, vanilla item delivery, and Vault money deposits. Actions that fail are recorded as `PENDING` for administrator review rather than being automatically replayed and potentially duplicated.

## Reward lore format

Reward and GUI lore use a YAML block list with quoted lines:

```yaml
Lore:
  - "&8✦ &7A little lore line."
  - "&b➤ &fA second line with a highlight."
```

Both `Lore:` and the legacy lowercase `lore:` key are supported. Legacy ampersand color codes are accepted, along with decorative Unicode symbols.

## Build

Requirements: JDK 21, Apache Maven 3.9 or newer, and internet access for dependency resolution.

```bash
mvn --batch-mode clean test package
```

The plugin JAR is written to:

```text
target/OctaRewardPlus-1.0.3-SNAPSHOT.jar
```

The GitHub Actions workflow is located at `.github/workflows/build.yml`. It runs tests and packages the plugin on pushes, pull requests, and manual workflow dispatches, then uploads the JAR as a downloadable workflow artifact.

Paper API, PlaceholderAPI, and VaultAPI are `provided` dependencies and are not shaded into the plugin. HikariCP, SQLite JDBC, and MySQL Connector/J are bundled by Maven Shade. UltimateRewards remains an optional runtime integration and is not a compile-time dependency.

## Installation

1. Build the JAR and place it in the Paper server's `plugins/` directory.
2. Start the server once. OctaReward Plus creates `plugins/OctaRewardPlus/` and its configuration files.
3. Configure `config.yml`, `gui.yml`, `messages.yml`, and the files in `rewards/`.
4. Restart after changing storage type or SQL connection settings. Use `/dailyreward reload` for reward, GUI, and message changes.
5. Back up `data/octarewardplus.db` regularly when using SQLite. Use the database host's backup process for MySQL/MariaDB.

## Configuration layout

```text
plugins/OctaRewardPlus/
├── config.yml
├── messages.yml
├── gui.yml
├── storage.yml
├── rewards/
│   ├── daily.yml
│   ├── weekly.yml
│   ├── monthly.yml
│   ├── streak.yml
│   ├── playtime.yml
│   ├── events.yml
│   ├── seasonal-events.yml
│   └── one-time.yml
└── data/
    └── octarewardplus.db
```

### Reward periods

`time.timezone` accepts a Java time-zone ID. `time.daily-reset` uses `HH:mm` and controls the start boundary for daily periods. Weekly periods use ISO weeks (Monday to Sunday), based on the configured timezone and reset time. Monthly periods use the calendar month, also based on that reset boundary.

Add reward definitions under the `rewards:` root in `daily.yml`, `weekly.yml`, or `monthly.yml`. Use the same reward fields in each file: `enabled`, `display-name`, `material`, `slot`, `permission`, `Lore`, and `actions`.

### Streak and playtime trails

`rewards/streak.yml` controls missed-day behavior and milestones:

- `RESET` starts a new streak after a missed daily period.
- `PAUSE` preserves the existing count after a gap.
- `GRACE_PERIOD` continues the streak when missed days do not exceed `grace-days`.

Streak rewards are triggered automatically when the player successfully claims a daily reward and reaches the configured milestone day.

In `rewards/playtime.yml`, each reward can set `required-minutes`, `repeat`, and `repeat-interval-minutes`. The GUI arranges these rewards in a winding five-row path. `DAILYREWARD_TRACKER` tracks online session time and applies AFK/world filters. `MINECRAFT_STATISTIC` reads Minecraft's `PLAY_ONE_MINUTE` statistic. The optional `ULTIMATEREWARDS_CUSTOM` setting is a source label for the UltimateRewards calculator registration; it does not expose a verified accumulated-playtime read API for OctaReward Plus.

### Seasonal event schedules

Seasonal definitions are stored in `rewards/seasonal-events.yml`, separate from manually configured and activity-triggered rewards in `rewards/events.yml`. This separate file is created for existing installations without replacing their event settings.

Example:

```yaml
events:
  winter_festival:
    enabled: true
    event-name: "Winter Festival"
    season: WINTER
    display-name: "&b❄ Winter Festival Gift"
    material: BLUE_ICE
    trigger:
      type: CUSTOM
      id: winter_festival
    availability:
      auto-start: true
      starts-at: "2026-12-01T00:00:00+07:00"
      ends-at: "2027-01-01T00:00:00+07:00"
      claim-limit-per-player: 1
    Lore:
      - "&7A seasonal present from the winter court."
    actions:
      - "[console] give %player% packed_ice 16"
```

`starts-at` and `ends-at` must be ISO-8601 timestamps with an offset. When `auto-start` is enabled, the plugin ensures a server-wide event instance exists for that configured schedule. The event becomes claimable only within its configured date window. You can also manually start or end events using the admin commands.

Activity-triggered event rewards support `CUSTOM`, `BLOCK_BREAK`, `ENTITY_KILL`, and `LEVEL_UP`. For `BLOCK_BREAK` and `ENTITY_KILL`, `target` can filter a material/entity type; `world` can restrict the world; `required-count` sets the persisted activity threshold.

### Commands

| Command | Purpose |
|---|---|
| `/dailyreward`, `/daily`, `/reward`, `/dailyrewards`, `/rewards` | Open the main reward menu |
| `/dailyreward daily` | Open daily rewards |
| `/dailyreward weekly` | Open weekly rewards |
| `/dailyreward monthly` | Open monthly rewards |
| `/dailyreward streak` | Open the daily streak trail |
| `/dailyreward playtime` | Open the playtime progression trail |
| `/dailyreward events` | Open seasonal and custom events |
| `/dailyreward onetime` | Open one-time rewards |
| `/dailyreward reload` | Reload reward, GUI, and message YAML files |
| `/dailyreward give <player> <reward-id>` | Run configured reward actions for an online player without changing claim eligibility |
| `/dailyreward reset <player> <reward-id>` | Clear claim ledger rows for a reward ID |
| `/dailyreward streak reset <player>` | Reset current streak and last daily period |
| `/dailyreward playtime set <player> <minutes>` | Set tracked playtime |
| `/dailyreward event trigger <player> <event-id>` | Start a player-specific event instance |
| `/dailyreward event triggerall <event-id>` | Start a server-wide event instance |
| `/dailyreward event end <event-id>` | Deactivate active instances for an event ID |
| `/dailyreward stats <player>` | Display stored player stats |
| `/dailyreward debug` | Show integration and storage status |

Legacy admin aliases `/dr`, `/orp`, and `/octarewardplus` are also retained.

### Permissions

| Permission | Default | Purpose |
|---|---|---|
| `dailyreward.use` | true | Open reward menus |
| `dailyreward.admin` | op | General administration, debug, and playtime edits |
| `dailyreward.reload` | op | Reload YAML configuration |
| `dailyreward.give` | op | Manually give a configured reward |
| `dailyreward.reset` | op | Reset claims or a player's streak |
| `dailyreward.event.manage` | op | Trigger and end events |
| `dailyreward.playtime.bypass` | op | Bypass playtime requirements |
| `dailyreward.streak.bypass` | op | Reserved for streak extensions |
| `dailyreward.cooldown.bypass` | op | Reserved for cooldown extensions |

### PlaceholderAPI

The plugin registers both `%octarewardplus_...%` and legacy `%dailyreward_...%` namespaces. The example dates in `rewards/seasonal-events.yml` are starting schedules; update their ISO-8601 timestamps when preparing events for a later year.

| Placeholder | Result |
|---|---|
| `%octarewardplus_daily_state%` | `CLAIMED`, `AVAILABLE`, or `LOCKED` for the first configured daily reward |
| `%octarewardplus_weekly_state%` | `CLAIMED`, `AVAILABLE`, or `LOCKED` for the first configured weekly reward |
| `%octarewardplus_monthly_state%` | `CLAIMED`, `AVAILABLE`, or `LOCKED` for the first configured monthly reward |
| `%octarewardplus_daily_claimed%` | Whether the first configured daily reward has been claimed |
| `%octarewardplus_weekly_claimed%` | Whether the first configured weekly reward has been claimed |
| `%octarewardplus_monthly_claimed%` | Whether the first configured monthly reward has been claimed |
| `%octarewardplus_daily_available%` | Whether the first configured daily reward is available |
| `%octarewardplus_weekly_available%` | Whether the first configured weekly reward is available |
| `%octarewardplus_monthly_available%` | Whether the first configured monthly reward is available |
| `%octarewardplus_daily_next_reset%` | Next daily reset timestamp |
| `%octarewardplus_weekly_next_reset%` | Next Monday reset timestamp |
| `%octarewardplus_monthly_next_reset%` | Next monthly reset timestamp |
| `%octarewardplus_streak_current%` | Current streak count |
| `%octarewardplus_streak_best%` | Best streak count |
| `%octarewardplus_streak_next_milestone%` | Next streak milestone day, or `0` when complete |
| `%octarewardplus_streak_progress%` | Percentage progress toward the next milestone |
| `%octarewardplus_playtime_total_minutes%` | Total tracked playtime in minutes |
| `%octarewardplus_playtime_formatted%` | Total playtime formatted as hours and minutes |
| `%octarewardplus_playtime_progress_<reward_id>%` | Progress percentage for a playtime reward |
| `%octarewardplus_playtime_claimed_<reward_id>%` | Whether the specified playtime milestone has been claimed |
| `%octarewardplus_reward_claimed_<category>_<reward_id>%` | Boolean claim status for a configured reward |
| `%octarewardplus_reward_available_<category>_<reward_id>%` | Boolean availability estimate for a configured reward |
| `%octarewardplus_reward_state_<category>_<reward_id>%` | `CLAIMED`, `AVAILABLE`, or `LOCKED` status for a configured reward |
| `%octarewardplus_event_available%` | Whether at least one configured event is active for the player |
| `%octarewardplus_event_active_count%` | Number of active configured events available to the player |
| `%octarewardplus_event_available_<event_id>%` | Whether a configured event is currently active for the player |
| `%octarewardplus_event_claimed_<event_id>%` | Whether the player's claim limit for an event has been reached |
| `%octarewardplus_onetime_available%` | Whether any enabled one-time reward remains unclaimed |

For dynamic placeholders, replace `<category>` with `daily`, `weekly`, `monthly`, `streak`, `playtime`, `events`, or `onetime`, and replace `<reward_id>` with the exact reward ID from the relevant YAML file. Streak milestone IDs use the `streak-<day>` format (for example, `streak-7`). Category-level daily/weekly/monthly state placeholders refer to the first configured reward in that category; use the per-reward placeholders when a category contains multiple rewards. Database-backed placeholders use asynchronous cached reads and never wait for a database response on the server thread. A value may briefly show its cached state immediately after a claim.

### Vault Economy

Vault is optional. With Vault and an economy provider installed (for example, EssentialsX Economy), reward files can include:

```yaml
actions:
  - "[money] 250"
  - "[message] &aYou received 250 in-game currency!"
```

`[money]` deposits a positive amount through the Vault provider. Action strings can use `%balance%` and `%balance_formatted%`. If Vault or an economy provider is unavailable, the money action fails and is logged; the plugin does not pretend the deposit succeeded.

## Performance and storage notes

Database queries run on a dedicated bounded executor and use a connection pool. SQLite uses a single connection with WAL mode and a busy timeout. Player playtime is sampled periodically; online tracking maps and per-player placeholder caches are cleared on quit, while cached offline player snapshots are bounded. Scheduled Bukkit tasks are owned by the plugin and are cancelled by Bukkit when the plugin disables. Storage connection settings require a restart after changes.

Claim rows use a unique key over `(uuid, reward_id, claim_type, period_key)`. The daily streak update is performed transactionally and advances at most once per daily period. Claims are recorded before reward actions execute. If an action fails after a claim is committed, its status remains `PENDING` for manual review; automatic replay is intentionally avoided to prevent duplicate item or currency delivery.

## Optional integrations and API

PlaceholderAPI and Vault are soft dependencies. The UltimateRewards hook uses runtime reflection so the plugin can load without UltimateRewards installed. Compatibility should still be checked against the exact UltimateRewards JAR installed on the target server.

Other plugins can obtain the public facade:

```java
DailyRewardPlugin octa = (DailyRewardPlugin) Bukkit.getPluginManager().getPlugin("OctaRewardPlus");
if (octa != null && octa.isEnabled()) {
    octa.getApi().triggerEvent("winter_festival", null); // null targets all players
    octa.getApi().getPlayerStats(player.getUniqueId()).thenAccept(stats -> {
        // stats.currentStreak(), stats.bestStreak(), stats.playtimeMillis()
    });
}
```

Database-backed API methods return `CompletableFuture`. Do not call `.get()` or `.join()` on the server thread.

## Compatibility and testing notes

The plugin is intended for Paper 1.21.11 and Java 21. The project includes unit tests for date and playtime calculations. The Maven source package must be built in CI or on a Maven-enabled machine to verify dependency resolution, compile compatibility, tests, and final shaded JAR output.


## Version 1.0.3-SNAPSHOT

- Rebuilt the GUI into four top-level sections: Basic Reward, Daily Streak, Playtime Reward, and Extra Reward.
- Basic Reward contains Daily Reward (Daily / Weekly / Monthly) and Supporter Reward (configurable rank rewards).
- Extra Reward contains Seasonal Reward collections.
- All inventory menus use six rows (54 slots).
- Item display and lore components explicitly disable Minecraft's default italic styling.
- Playtime rewards now include 1-hour through 100-hour milestones.
- Daily streak milestones are configured for every day from 1 through 100 and are granted automatically on the matching consecutive day.
- Supporter rewards are permission-gated and work with LuckPerms through Bukkit permission checks (for example `group.vip`).
- Daily, weekly, and monthly reward menus display their next reset countdown in the item lore.
- Reward menus use a dark purple / magenta / black glass-pane palette.

### Supporter reward configuration

Add rank entries under `ranks:` in `plugins/OctaRewardPlus/rewards/supporter.yml`. Each entry supports `display-name`, `material`, `permission`, `Lore`, and `actions`. Rewards are claimable once per player.


### Supporter placeholders

- `%octarewardplus_reward_claimed_supporter_supporter_vip%` returns `true` or `false`.
- `%octarewardplus_reward_state_supporter_supporter_vip%` returns `CLAIMED`, `AVAILABLE`, or `LOCKED`.
- `%octarewardplus_reward_available_supporter_supporter_vip%` returns `true` or `false`.

The same dynamic reward placeholders are available through the legacy `%dailyreward_...%` identifier.


### Database build profiles and plugin size

The default Maven build targets SQLite and does not bundle the MySQL Connector/J driver, keeping the default plugin artifact smaller. If `storage.yml` is configured with `type: MYSQL`, build the MySQL-enabled artifact with:

```bash
mvn -Pmysql clean test package
```

The GitHub Actions workflow publishes both variants as separate artifacts. Paper API, PlaceholderAPI, and Vault API remain provided dependencies and are not bundled into the plugin JAR.
