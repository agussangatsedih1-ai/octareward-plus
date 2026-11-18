# OctaReward Plus

A standalone reward progression plugin for Paper servers. Keep players returning with daily, weekly, and monthly claim cycles, a day-by-day streak trail, 100 hourly playtime milestones, permission-based supporter rewards, and seasonal celebrations.

## Project metadata

- **Plugin ID:** `OctaRewardPlus`
- **Version:** `1.0.5-SNAPSHOT`
- **Author:** `Alvran`
- **Description:** Turn every return into an adventure with daily treasures, weekly and monthly milestones, seasonal celebrations, and winding playtime and streak journeys.
- **Target:** Paper 1.21.11 / Java 21

## Dependencies

- **EssentialsX is required.** The plugin descriptor declares `Essentials` as a hard dependency. Playtime milestones read the same Bukkit playtime statistic used by EssentialsX `/playtime`, so both features follow the server's persisted playtime counter.
- **PlaceholderAPI is optional** and enables the `%octarewardplus_...%` and legacy `%dailyreward_...%` placeholders.
- **Vault is optional** and enables `[money]` reward actions when an economy provider is present.
- **LuckPerms is optional.** Supporter rewards can use the `luckperms-group` setting for direct inherited-group checks, or the `octarewardplus.claim.support.<group>` permission node.

## Features

- **Daily / Weekly / Monthly Rewards** — separate player claim cooldowns. Defaults are 24 hours, 168 hours, and 720 hours, respectively. Each type has its own cooldown and reward action list.
- **Daily Streak** — consecutive daily claims advance the streak once per configured daily reset period. The 100 default milestones are manually claimable once the player's best consecutive-day streak has reached the matching day; reaching a milestone once keeps it unlocked even if the active streak later resets.
- **Playtime Rewards** — the default catalog has one claimable milestone every hour from hour 1 through hour 100. Add more entries to `rewards/playtime.yml` to continue beyond 100 hours. Progress is read from the EssentialsX-compatible Bukkit playtime statistic.
- **Premium Supporter Rewards** — rank caches appear inside Basic Reward → Daily Reward. Each configured rank cache has its own 24-hour rolling cooldown and can be gated by a LuckPerms group or `octarewardplus.claim.support.<group>` permission.
- **Seasonal Rewards** — Winter, Christmas, Spring, Easter, and custom event collections with configurable start/end timestamps.
- **Fancy GUI** — six-row (54-slot) menus, distinct available/cooldown/claimed/locked states, and winding 100-day / 100-hour tracks. Progression panes are green when claimed, yellow when available, and red when locked. Generated item text explicitly disables italic styling.
- **Storage** — SQLite by default or MySQL/MariaDB through the `mysql` Maven profile.

Reward actions support console commands, optionally enabled player commands, messages, broadcasts, titles, action bars, sounds, item delivery, and optional Vault deposits. Daily/weekly/monthly reward definitions can also include `premium-actions`; the premium list is used when the player has the category's configured premium permission.

## Reward lore format

```yaml
Lore:
  - "&8✦ &7A little lore line."
  - "&b➤ &fA second line with a highlight."
```

Legacy ampersand color codes and decorative Unicode symbols are supported. Plugin-generated item display names and lore disable Minecraft's default italic decoration unless the text explicitly chooses otherwise.

## Build

Requirements: JDK 21, Apache Maven 3.9 or newer, and internet access for dependency resolution.

```bash
mvn --batch-mode clean test package
```

For MySQL builds (the MySQL Connector/J driver is included):

```bash
mvn --batch-mode -Pmysql clean test package
```

The plugin JAR is written to `target/OctaRewardPlus-1.0.5-SNAPSHOT.jar`. The GitHub Actions workflow is located at `.github/workflows/build.yml`; it builds and uploads SQLite and MySQL artifacts separately.

Paper API and PlaceholderAPI are `provided` compile dependencies and are not shaded into the plugin. EssentialsX remains a mandatory runtime plugin dependency; its playtime statistic is read through Bukkit. Vault and LuckPerms are optional runtime integrations loaded without compile-time API dependencies. SQLite JDBC and HikariCP are bundled; MySQL Connector/J is included by the `mysql` profile.

## Installation

1. Install EssentialsX on the Paper server. OctaReward Plus will not enable without it.
2. Place the built OctaReward Plus JAR in `plugins/`.
3. Start the server once to generate `plugins/OctaRewardPlus/`.
4. Configure `config.yml`, `gui.yml`, `messages.yml`, and the reward files.
5. Restart after changing storage type or SQL connection settings. Use `/dailyreward reload` for reward, GUI, and message changes.

## Reward configuration

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
│   ├── supporter.yml
│   ├── events.yml
│   ├── seasonal-events.yml
│   └── one-time.yml
└── data/
    └── octarewardplus.db
```

### Daily, weekly, and monthly cooldowns

The claim cycle follows a cooldown-duration model. After a successful claim, the next claim becomes available after the configured number of hours. Defaults: `daily: 24`, `weekly: 168`, `monthly: 720`. These values are independent and can be changed under `periodic-rewards` in `config.yml`. Daily, Weekly, and Monthly category buttons open their respective reward pages. Click the reward item on that page to claim it. The cooldown model follows independent rolling cooldowns. The Premium Supporter submenu is also located inside this Daily Reward selector.

Example configuration:

```yaml
periodic-rewards:
  daily:
    enabled: true
    cooldown-hours: 24
    cooldown-format: "%hours%:%minutes%:%seconds%"
    premium-permission: "dailyreward.daily.premium"
  weekly:
    enabled: true
    cooldown-hours: 168
    premium-permission: "dailyreward.weekly.premium"
  monthly:
    enabled: true
    cooldown-hours: 720
    premium-permission: "dailyreward.monthly.premium"
  supporter:
    enabled: true
    cooldown-hours: 24
```

Each reward entry can use `actions` for the regular claim and `premium-actions` for players with the configured premium permission. The cooldown is shared by all rewards within the same category, so claiming a daily reward starts that player's daily cooldown. Monthly uses a fixed 720-hour duration (30 days), matching the reference configuration rather than a variable-length calendar month.

### Daily streak

`rewards/streak.yml` contains `milestones` numbered from day 1 through day 100 by default. Streak progression is advanced when a player successfully claims the daily reward. Once a milestone day is reached, its tile turns yellow and can be manually claimed. Claimed milestones turn green; future milestones remain red. `missed-day-policy` supports `RESET`, `PAUSE`, and `GRACE_PERIOD`; `grace-days` controls the grace window.

### Playtime milestones

`rewards/playtime.yml` contains 100 default entries, `playtime_1h` through `playtime_100h`, with `required-minutes` increasing by 60 for each entry (60 to 6000). Each milestone can be claimed once. To extend progression, add another reward entry with the next ID and `required-minutes: 6060` for hour 101, then continue in 60-minute increments. The GUI places rewards along a winding path and paginates when needed.

The playtime source is the same persistent statistic used by EssentialsX `/playtime`; it is not an AFK-time estimate. The sync interval controls how often the database display snapshot is refreshed. Claim eligibility reads the live statistic for online players.

### Premium Supporter rank rewards

Add any number of entries under `ranks:` in `rewards/supporter.yml`. These rewards are opened from Basic Reward → Daily Reward → Premium Supporter. Each cache has an independent 24-hour cooldown by default; the cooldown starts when that specific cache is claimed.

Use `luckperms-group` to bind a reward directly to a LuckPerms group, including inherited groups:

```yaml
ranks:
  supporter_legend:
    enabled: true
    display-name: "&6✦ &lLegend Supporter Cache"
    material: NETHER_STAR
    slot: 24
    luckperms-group: "legend"
    Lore:
      - "&6✧ &fA daily gift for the Legend rank."
    actions:
      - "[console] give %player% nether_star 1"
```

Alternatively, use a custom `permission:` node instead of `luckperms-group:`. The default node pattern is `octarewardplus.claim.support.<group>`, for example:

```text
/lp group vip permission set octarewardplus.claim.support.vip true
/lp group elite permission set octarewardplus.claim.support.elite true
/lp group legend permission set octarewardplus.claim.support.legend true
```

When both `permission` and `luckperms-group` are configured, either matching access route is accepted. Legacy `octarewardplus.supporter.<group>` permission entries from older supporter.yml files remain recognized.

### Seasonal events

Seasonal definitions are stored in `rewards/seasonal-events.yml` and `rewards/events.yml`. Configure an event's ISO-8601 `starts-at` and `ends-at` timestamps, and use the event's `season` to place it in its seasonal collection. Example schedules should be updated for the year in which you plan to run the event.

## Commands

| Command | Purpose |
|---|---|
| `/dailyreward`, `/daily`, `/reward`, `/dailyrewards`, `/rewards` | Open the main menu |
| `/dailyreward daily` | Open Daily rewards |
| `/dailyreward weekly` | Open Weekly rewards |
| `/dailyreward monthly` | Open Monthly rewards |
| `/dailyreward streak` | Open the daily streak trail |
| `/dailyreward playtime` | Open the playtime trail |
| `/dailyreward supporter` | Open rank rewards |
| `/dailyreward events` | Open seasonal/custom events |
| `/dailyreward reload` | Reload reward, GUI, and message YAML |
| `/dailyreward give <player> <reward-id>` | Manually execute a reward's actions |
| `/dailyreward reset <player> <reward-id>` | Clear a reward claim |
| `/dailyreward streak reset <player>` | Reset a player's streak |
| `/dailyreward playtime set <player> <minutes>` | Set the online player's playtime statistic |
| `/dailyreward event trigger <player> <event-id>` | Trigger a player-specific event |
| `/dailyreward event triggerall <event-id>` | Trigger an event for all players |
| `/dailyreward event end <event-id>` | End an event |
| `/dailyreward stats <player>` | Display player reward stats |

### Permissions

| Permission | Default | Purpose |
|---|---|---|
| `dailyreward.use` | true | Open reward menus |
| `dailyreward.admin` | op | General administration and playtime edits |
| `dailyreward.reload` | op | Reload YAML configuration |
| `dailyreward.give` | op | Manually give a configured reward |
| `dailyreward.reset` | op | Reset claims or a streak |
| `dailyreward.event.manage` | op | Trigger and end events |
| `dailyreward.playtime.bypass` | op | Bypass playtime requirements |
| `dailyreward.cooldown.bypass` | op | Bypass periodic reward cooldowns |
| `dailyreward.daily.premium` | false | Use daily `premium-actions` |
| `dailyreward.weekly.premium` | false | Use weekly `premium-actions` |
| `dailyreward.monthly.premium` | false | Use monthly `premium-actions` |
| `octarewardplus.claim.support.vip` | false | Claim the VIP premium daily cache when using permission-based access |
| `octarewardplus.claim.support.elite` | false | Claim the Elite premium daily cache when using permission-based access |
| `octarewardplus.claim.support.legend` | false | Claim the Legend premium daily cache when using permission-based access |

For custom groups, use the same `octarewardplus.claim.support.<group>` pattern or set `luckperms-group: <group>` in the reward entry.

## PlaceholderAPI

The plugin registers `%octarewardplus_...%` and legacy `%dailyreward_...%` namespaces. Examples:

| Placeholder | Result |
|---|---|
| `%octarewardplus_daily_state%` | `AVAILABLE`, `COOLDOWN`, or `LOCKED` |
| `%octarewardplus_weekly_state%` | `AVAILABLE`, `COOLDOWN`, or `LOCKED` |
| `%octarewardplus_monthly_state%` | `AVAILABLE`, `COOLDOWN`, or `LOCKED` |
| `%octarewardplus_daily_claimed%` | Whether the daily category is on cooldown |
| `%octarewardplus_weekly_claimed%` | Whether the weekly category is on cooldown |
| `%octarewardplus_monthly_claimed%` | Whether the monthly category is on cooldown |
| `%octarewardplus_daily_available%` | Whether daily rewards can currently be claimed |
| `%octarewardplus_weekly_available%` | Whether weekly rewards can currently be claimed |
| `%octarewardplus_monthly_available%` | Whether monthly rewards can currently be claimed |
| `%octarewardplus_daily_cooldown%` | Formatted time remaining until daily is available |
| `%octarewardplus_weekly_cooldown%` | Formatted time remaining until weekly is available |
| `%octarewardplus_monthly_cooldown%` | Formatted time remaining until monthly is available |
| `%octarewardplus_streak_current%` | Current consecutive-day streak |
| `%octarewardplus_streak_best%` | Best streak |
| `%octarewardplus_streak_next_milestone%` | Next configured streak day |
| `%octarewardplus_playtime_total_minutes%` | Total playtime in minutes |
| `%octarewardplus_playtime_formatted%` | Total playtime formatted as hours/minutes |
| `%octarewardplus_playtime_progress_<reward_id>%` | Progress toward a playtime milestone |
| `%octarewardplus_reward_claimed_<category>_<reward_id>%` | Claim/cooldown status of a reward |
| `%octarewardplus_reward_available_<category>_<reward_id>%` | Whether a reward can currently be claimed |
| `%octarewardplus_reward_state_<category>_<reward_id>%` | `AVAILABLE`, `COOLDOWN`, `CLAIMED`, or `LOCKED` |
| `%octarewardplus_event_available%` | Whether any configured event is active for the player |
| `%octarewardplus_event_claimed_<event_id>%` | Whether the player reached the event claim limit |

For dynamic placeholders, use category values `daily`, `weekly`, `monthly`, `playtime`, `streak`, `events`, `onetime`, or `supporter` and exact reward IDs from YAML. Asynchronous database-backed placeholders may briefly display their previous cache value immediately after a claim.

## Vault Economy

Vault is optional. With Vault and a compatible economy provider installed, rewards can include:

```yaml
actions:
  - "[money] 250"
  - "[message] &aYou received 250 in-game currency!"
```

The `[money]` action deposits a positive amount. The plugin does not pretend a deposit succeeded when the provider is unavailable.

## Performance and storage

Database I/O runs asynchronously on a bounded worker pool. SQLite uses WAL mode and a busy timeout; MySQL uses a connection pool. Online playtime is sampled at the configured sync interval, while claim eligibility reads the live statistic. Per-player placeholder and offline-stat caches are bounded and invalidated when player state changes. The plugin does not track estimated AFK minutes.

Claims are persisted before configured actions execute. If an action fails after a claim is committed, it remains `PENDING` for administrator review rather than being automatically replayed and risking duplicate delivery. Back up the SQLite database or your MySQL database regularly.

## API

Other plugins can access the public API facade:

```java
DailyRewardPlugin octa = (DailyRewardPlugin) Bukkit.getPluginManager().getPlugin("OctaRewardPlus");
if (octa != null && octa.isEnabled()) {
    octa.getApi().triggerEvent("winter_festival", null); // null targets all players
    octa.getApi().getPlayerStats(player.getUniqueId()).thenAccept(stats -> {
        // stats.currentStreak(), stats.bestStreak(), stats.playtimeMillis()
    });
}
```

Database-backed API methods return `CompletableFuture`; do not block the server thread with `.get()` or `.join()`.

## Compatibility and verification

Target: Paper 1.21.11 and Java 21. Build and test with `mvn clean test package` or run the GitHub Actions workflow. A source ZIP or a successful YAML check does not by itself prove the plugin JAR compiles.
