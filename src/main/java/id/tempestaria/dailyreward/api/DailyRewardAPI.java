package id.tempestaria.dailyreward.api;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.db.PlayerStats;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public integration facade for other plugins. */
public final class DailyRewardAPI {
    private final DailyRewardPlugin plugin;
    public DailyRewardAPI(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public CompletableFuture<PlayerStats> getPlayerStats(UUID playerId) { return plugin.getDatabase().getStats(playerId); }
    public CompletableFuture<Long> getTotalPlaytimeMinutes(UUID playerId) { return plugin.getDatabase().getPlaytime(playerId).thenApply(ms -> ms / 60_000L); }

    /** Creates an Event Reward instance. A null player makes it available server-wide. */
    public CompletableFuture<String> triggerEvent(String rewardId, Player player) {
        Optional<RewardDefinition> opt = plugin.getCatalog().findEvent(rewardId);
        if (opt.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown event reward: " + rewardId));
        RewardDefinition reward = opt.get();
        long now = System.currentTimeMillis();
        long start = parse(reward.startsAt(), now);
        long end = parse(reward.endsAt(), now + 86_400_000L);
        return plugin.getDatabase().triggerEvent(reward.id(), player == null ? null : player.getUniqueId(), start, end)
                .thenApply(instance -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(new DailyRewardEventTriggeredEvent(reward.id(), instance, player));
                        if (player == null) plugin.invalidateAllPlaceholderStates();
                        else plugin.invalidatePlaceholderState(player.getUniqueId());
                    });
                    if (player != null) plugin.getDatabase().audit(null, player.getUniqueId(), "API_EVENT_TRIGGER", reward.id());
                    else plugin.getDatabase().audit(null, null, "API_EVENT_TRIGGER_ALL", reward.id());
                    return instance;
                });
    }

    private long parse(String value, long fallback) {
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); }
        catch (Exception ignored) { return fallback; }
    }
}
