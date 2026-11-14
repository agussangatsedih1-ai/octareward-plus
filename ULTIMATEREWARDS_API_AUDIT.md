# UltimateRewards integration verification notes

The bridge intentionally has no compile-time imports from UltimateRewards.

| Documented item | Verified fact from the published docs | How this project uses it |
|---|---|---|
| Event package | `eu.athelion.ultimaterewards.api.event` | Loads the documented event class names at runtime and registers Bukkit listeners only when they exist |
| `PlayerPreClaimRewardEvent` | Cancellable; getters include `getClaimer()`, `getClaimedReward()`, `getRewardActionsToExecute()` | Observes only; does not modify actions or cancel claims |
| `PlayerClaimedRewardEvent` | Fires after successful claim; getters include `getClaimer()` and `getClaimedReward()` | Passive audit/debug logging |
| `ReminderReceiveEvent` | Cancellable reminder event; `getReceiver()`, `getAvailableRewards()` | Passive; does not change reminder delivery |
| `UserLoadedEvent` / `UserUnloadEvent` | User lifecycle events; `UserLoadedEvent` may be async | Passive observation; handler does not call Bukkit/player APIs |
| Reward lookup | `UltimateReward.getRewardByName(String)` returns an optional reward; `isReward(String)` is also documented | Optional reflective lookup of `getRewardByName(String)` |
| Playtime calculator | Static `registerPlayTimeCalculator(...)`; documentation code shows `calculatePlayTime(OfflinePlayer)` returning minutes | Opt-in reflective proxy; locates the interface and registration method from the installed plugin JAR |

## Documentation used

- https://ultimaterewards.athelion.eu/api/events
- https://ultimaterewards.athelion.eu/api/examples
- https://ultimaterewards.athelion.eu/api/setting-up-own-playtime-calculator
- https://ultimaterewards.athelion.eu/configuration/rewards/reward-types/time-fixed-reward
- https://ultimaterewards.athelion.eu/configuration/rewards/reward-types/streak-fixed-reward
- https://ultimaterewards.athelion.eu/configuration/rewards/reward-types/play-time-reward

## Important verification limitation

No UltimateRewards JAR was supplied with this project, and the documentation pages do not identify a resolvable official Maven coordinate or the fully qualified name of `UltimateReward` / `PlayTimeCalculator`. Consequently, no particular UltimateRewards release or API JAR version is claimed as tested. The runtime bridge checks classes/methods in the JAR installed on the target server and emits warnings when a documented capability is absent. Keep the custom calculator disabled until you have tested the exact JAR on a staging server.


**Paper 1.21.11 playtime statistic note:** Paper API exposes `Statistic.PLAY_ONE_MINUTE` (its name is misleading; it stores ticks played). The code therefore uses that Paper enum and converts 1,200 ticks to one minute. The UltimateRewards documentation example mentioning `PLAY_ONE_TICK` is not a Paper 1.21.11 enum constant.
