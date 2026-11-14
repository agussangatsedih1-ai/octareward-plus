# OctaReward Plus

## Project metadata

- **Plugin name:** OctaReward Plus (Bukkit/Paper plugin ID: `OctaRewardPlus`)
- **Version:** `0.0.1-SNAPSHOT`
- **Author:** `Alvran`
- **Description:** `Suprise me please`
- **Target:** Paper 1.21.11 / Java 21

Standalone rewards plugin for **Paper 1.21.11** / **Java 21**. The plugin's own storage, eligibility rules, claim ledger, GUI, and reward actions work independently of UltimateRewards.

## Implemented systems

- **Daily Reward** — configurable calendar reset (`Asia/Jakarta`, `00:00` by default), timezone-aware period key, and multiple reward IDs that may each be claimed once during the same daily period.
- **Daily Streak** — streak increases only on the first successful daily claim in a reset period. `RESET`, `PAUSE`, and `GRACE_PERIOD` policies are supported. Configurable milestone rewards.
- **Playtime Reward** — minute thresholds, optional repeated interval claims, AFK policy, excluded worlds, periodic saves, and quit/shutdown flushes. `DAILYREWARD_TRACKER` and `MINECRAFT_STATISTIC` sources are implemented for OctaReward Plus's own thresholds.
- **Event Reward** — admin/API triggers, plus Bukkit `BLOCK_BREAK`, `ENTITY_KILL`, and `LEVEL_UP` triggers. Supports target/world filters, activity counts, start/end timestamps, player/global instances, and per-player claim limits.
- **One Time Reward** — one claim per UUID/reward ID, optional permission, first-join condition, and start/end timestamps.

Reward actions are recorded as DONE only when the configured command/action dispatch reports success; commands that the server does not handle remain PENDING for administrator review.

The GUI uses an inventory holder and PersistentDataContainer action keys. Category inventories paginate automatically when a category exceeds the available content slots; previous/next/back positions and reward item slots are configurable. All clicks and drags in the plugin GUI are cancelled; claim requirements are checked again by the service and database when the player clicks. The action engine supports console/player commands (player commands opt-in), messages, broadcast, title/subtitle, action bar, sound, vanilla item delivery, and Vault money deposits when an economy provider is available.

## Reward lore format

Reward and GUI lore uses a YAML block list with quoted lines:

```yaml
Lore:
  - "Lore"
```

The loader also accepts the previous lowercase `lore` key for existing configurations.

## Build status of this source export

This package contains the complete Java source, Maven build definition, resources, and tests. A plugin JAR is **not included**: Maven is not installed in the preparation environment, so a full Paper-linked compile/package could not be completed here. Build it on a machine with JDK 21, Maven 3.9+, and network access using the command below.

## Build

Requirements: JDK 21 and Apache Maven 3.9 or newer. Network access is required on the first build so Maven can resolve Paper and the library dependencies.

```bash
mvn clean test package
```

The plugin JAR is written to:

```text
target/OctaRewardPlus-0.0.1-SNAPSHOT.jar
```

### GitHub Actions build

The repository includes `.github/workflows/build.yml`. GitHub Actions automatically runs the Maven tests and package build when code is pushed, a pull request is opened or updated, or the workflow is started manually from the **Actions** tab. The generated plugin JAR is uploaded as a workflow artifact named `OctaRewardPlus-<commit-sha>` and can be downloaded from the completed workflow run.

Paper API, PlaceholderAPI 2.11.7, and VaultAPI 1.7.1 use Maven `provided` scope and are not bundled. HikariCP, SQLite JDBC, and MySQL Connector/J are included in the output JAR by Maven Shade. UltimateRewards is deliberately **not** a compile-time dependency.

## Install

1. Build the JAR and put it in the Paper server's `plugins/` directory.
2. Start the server once. OctaReward Plus creates `plugins/OctaRewardPlus/` and the example YAML files.
3. Edit `config.yml`, `gui.yml`, `messages.yml`, and the files in `rewards/`.
4. Restart the server after changing `storage.type` or SQL connection settings. Reward and GUI changes can be loaded with `/dailyreward reload`.
5. Keep a regular backup of `data/octarewardplus.db` when using SQLite. For MySQL, use your database host's normal backup process.

## Configuration layout

```text
plugins/OctaRewardPlus/
├── config.yml
├── messages.yml
├── gui.yml
├── storage.yml
├── rewards/
│   ├── daily.yml
│   ├── streak.yml
│   ├── playtime.yml
│   ├── events.yml
│   └── one-time.yml
└── data/
    └── octarewardplus.db
```

### Time and streak

`time.timezone` accepts a Java time-zone ID. `time.daily-reset` uses `HH:mm`. Claim periods are derived from the configured local reset, not from a 24-hour cooldown after a player's last claim.

`rewards/streak.yml`:

- `missed-day-policy: RESET` starts a new streak after a missed daily period.
- `missed-day-policy: PAUSE` preserves the existing count after a gap, then continues from it on the next successful daily claim.
- `missed-day-policy: GRACE_PERIOD` continues the streak when the number of missed days is no greater than `grace-days`.

### Playtime

- `DAILYREWARD_TRACKER` tracks online session time and applies AFK/world filters.
- `MINECRAFT_STATISTIC` reads `Statistic.PLAY_ONE_MINUTE` and converts 1,200 ticks to one minute. It reflects Minecraft's total statistic and does not independently filter AFK or excluded worlds.
- `ULTIMATEREWARDS_CUSTOM` is the source label for the optional UltimateRewards calculator integration; OctaReward Plus's own rewards fall back to its tracker because the documented API does not expose a verified read method for UltimateRewards' accumulated player playtime.

`integrations.ultimaterewards.custom-playtime-calculator.enabled` is **false by default**. When enabled and the bridge finds the registration method/interface in the installed UltimateRewards JAR, the calculator is registered with UltimateRewards. This affects UltimateRewards' playtime calculation globally; enable it only when that is intentional.

### Event trigger types

For an event reward under `events:` in `rewards/events.yml`:

```yaml
trigger:
  type: BLOCK_BREAK # CUSTOM, BLOCK_BREAK, ENTITY_KILL, LEVEL_UP
  id: stone_mining
  target: STONE
  world: world
  required-count: 10
```

`target` and `world` can be left empty to accept any target/world. `required-count` progress is persisted and the trigger is raised only once per player for that event reward. `CUSTOM` events are started through admin commands or the public API. For custom events, commands/API accept either the reward key (for example `weekend_bonus`) or its configured `trigger.id` alias (for example `weekend_adventure`).

## Commands

| Command | Purpose |
|---|---|
| `/dailyreward` or `/orp` or `/octarewardplus` | Open the main GUI |
| `/dailyreward daily` | Open Daily Reward |
| `/dailyreward streak` | Open streak status/milestones |
| `/dailyreward playtime` | Open Playtime Reward |
| `/dailyreward events` | Open Event Reward |
| `/dailyreward onetime` | Open One Time Reward |
| `/dailyreward reload` | Reload YAML files (storage connection settings require restart) |
| `/dailyreward give <player> <reward-id>` | Run the selected configured reward actions for an online player; does not alter claim eligibility |
| `/dailyreward reset <player> <reward-id>` | Clear claim ledger rows for that ID |
| `/dailyreward streak reset <player>` | Reset current streak and last daily period |
| `/dailyreward playtime set <player> <minutes>` | Set tracked playtime |
| `/dailyreward event trigger <player> <event-id>` | Start a player-specific event instance |
| `/dailyreward event triggerall <event-id>` | Start a server-wide event instance |
| `/dailyreward event end <event-id>` | Deactivate active instances for that event ID |
| `/dailyreward stats <player>` | Display stored player stats |
| `/dailyreward debug` | Show integration/storage status |

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `dailyreward.use` | true | Open reward menus |
| `dailyreward.admin` | op | General admin tools / debug / set playtime |
| `dailyreward.reload` | op | Reload YAML |
| `dailyreward.give` | op | Manually grant configured actions |
| `dailyreward.reset` | op | Reset claims/streak |
| `dailyreward.event.manage` | op | Trigger/end events |
| `dailyreward.streak.bypass` | op | Reserved for streak restriction extensions |
| `dailyreward.cooldown.bypass` | op | Reserved for cooldown restriction extensions |
| `dailyreward.playtime.bypass` | op | Bypass playtime requirement |

## PlaceholderAPI

When PlaceholderAPI is installed, OctaReward Plus registers the `octarewardplus` namespace and the legacy `dailyreward` namespace:

- `%octarewardplus_daily_state%` (legacy alias: `%dailyreward_daily_state%`)
- `%octarewardplus_daily_next_reset%` (legacy alias: `%dailyreward_daily_next_reset%`)
- `%octarewardplus_streak_current%` (legacy alias: `%dailyreward_streak_current%`)
- `%octarewardplus_streak_best%` (legacy alias: `%dailyreward_streak_best%`)
- `%octarewardplus_playtime_total_minutes%` (legacy alias: `%dailyreward_playtime_total_minutes%`)
- `%octarewardplus_playtime_progress_<reward_id>%` (legacy alias: `%dailyreward_playtime_progress_<reward_id>%`)
- `%octarewardplus_event_available%` (legacy alias: `%dailyreward_event_available%`)
- `%octarewardplus_onetime_available%` (legacy alias: `%dailyreward_onetime_available%`)

Placeholder callbacks use cached state or asynchronous database reads cached on first request; they do not wait for a database response on the server thread. As with any placeholder system, a just-requested value can briefly show the cached value until the async read completes.

## Vault Economy

Vault is an optional soft dependency. When Vault and an economy provider (for example, EssentialsX Economy) are installed, reward action files may use:

```yaml
actions:
  - "[money] 250"
  - "[message] &aYou received 250 in-game currency!"
```

The `[money]` action deposits the configured positive amount through the Vault Economy provider. Action strings can also use `%balance%` (plain number) and `%balance_formatted%` (provider-formatted amount). It fails safely and leaves the claim marked for review if Vault or an economy provider is unavailable; it does not silently pretend that currency was granted. VaultAPI is a `compileOnly` dependency and is not bundled into the plugin JAR. Configure `integrations.vault.enabled` and `integrations.vault.required` in `config.yml` to control the optional hook.

## Public API

Other plugins can obtain the facade through Bukkit's plugin manager:

```java
DailyRewardPlugin dailyReward = (DailyRewardPlugin) Bukkit.getPluginManager().getPlugin("OctaRewardPlus");
if (dailyReward != null && dailyReward.isEnabled()) {
    dailyReward.getApi().triggerEvent("weekend_bonus", player); // pass null for server-wide
    dailyReward.getApi().getPlayerStats(player.getUniqueId()).thenAccept(stats -> {
        // Use stats.currentStreak(), stats.bestStreak(), stats.playtimeMillis().
    });
}
```

The main Java class remains `id.tempestaria.dailyreward.DailyRewardPlugin` for source compatibility. The Bukkit plugin ID is `OctaRewardPlus`; the display/log prefix is `OctaReward Plus`.

The API methods return `CompletableFuture` for database-backed operations. Do not block the server thread with `.get()` or `.join()`.

OctaReward Plus also fires Bukkit events:

- `DailyRewardPreClaimEvent` — cancellable, before a player claim starts.
- `DailyRewardClaimedEvent` — after a claim succeeds and actions are attempted.
- `DailyRewardStreakChangeEvent` — after the stored current streak changes.
- `DailyRewardEventTriggeredEvent` — after an event instance is created.

## Storage / duplicate-claim protection

SQLite is the default. MySQL is supported for a shared database. Player identity is UUID. Claim rows have a compound primary key over `(uuid, reward_id, claim_type, period_key)`. Daily streak advancement has a separate unique period marker so multiple daily rewards do not increment a streak more than once in the same period. Multi-server daily/event claims use DB transactions and player-row locks where supported, in addition to unique constraints.

Database work runs on a dedicated executor, not the Bukkit main thread. The schema is initialized idempotently and records schema version `2` in `dr_meta`. Back up before upgrading or editing the database manually.

Claims are committed before reward actions run. Each claim row starts as `PENDING` and is changed to `DONE` when all configured actions return without an error. If the server/player goes offline at the wrong time or an action fails after some earlier actions have run, the row remains `PENDING`; the plugin deliberately does **not** replay it automatically, because replaying a partially executed action list could duplicate item delivery. Review pending rows in `dr_claims` and reconcile them manually.

## UltimateRewards API audit

| API capability | Evidence in official docs | Runtime implementation | JAR verification |
|---|---|---|---|
| Bukkit event package | `eu.athelion.ultimaterewards.api.event`; events named on the Events page | Optional runtime registration for documented event class names | Not verified against a specific JAR in this build environment |
| `PlayerPreClaimRewardEvent` | `getClaimer()`, `getClaimedReward()`, `getRewardActionsToExecute()`; cancellable | Observed passively; no mutation/cancellation is performed | Not verified against a specific JAR |
| `PlayerClaimedRewardEvent` | `getClaimer()`, `getClaimedReward()` | Observed for audit/debug logging | Not verified against a specific JAR |
| Reward lookup | `UltimateReward.getRewardByName(String)` and `UltimateReward.isReward(String)` | Optional lookup uses reflection if the class/method exists | Not verified against a specific JAR |
| Custom calculator | `registerPlayTimeCalculator(...)`; code example uses `OfflinePlayer` and returns minutes | Opt-in reflective interface proxy; runtime registration method and interface are discovered from the installed JAR | Not verified against a specific JAR |

The official documentation does not specify a Maven/Gradle coordinate or package for the `UltimateReward` class / `PlayTimeCalculator` interface in the cited pages. For that reason this project does not guess a compile dependency, group ID, artifact ID, or registration signature. The optional bridge scans the installed UltimateRewards plugin JAR and checks the required names/signatures at runtime. Supply and test against the exact UltimateRewards JAR used on your server before enabling its custom calculator.

## Test coverage and build status

Unit tests are included for reset-period calculations, streak gap policies, and tick/minute conversion. The code also includes runtime DB uniqueness/transaction guards, but a full Paper test-server or MockBukkit integration suite still needs to be run against the exact server/plugin versions.

**Build status for this handoff:** source project and Maven `pom.xml` are prepared. A JAR could not be built in this environment because Maven is not installed. Do not treat this handoff as a verified successful compile. Run `mvn clean test package` in a network-enabled Java 21 environment, then test on a staging Paper 1.21.11 server with the exact UltimateRewards JAR.


## Test coverage and known boundaries

Two focused JUnit test classes cover timezone/reset-period calculations, the three streak-gap policies, and tick-to-minute conversion. YAML resources were parsed successfully and the standalone utility smoke tests passed in the source-preparation environment. Paper integration tests, SQLite/MySQL concurrency tests, the complete Maven test suite, and the final JAR build still need to be run in a Maven-enabled Paper development environment.

Event claim limits in this version support a per-player maximum across event instances plus the database's per-instance duplicate guard. Separate per-day and elapsed-cooldown claim policies are not yet implemented.
