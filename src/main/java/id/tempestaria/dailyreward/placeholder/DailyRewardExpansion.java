package id.tempestaria.dailyreward.placeholder;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.DateUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DailyRewardExpansion extends PlaceholderExpansion {
    private final DailyRewardPlugin plugin;
    private final String identifier;
    private final Map<String, CompletableFuture<Boolean>> stateCache = new ConcurrentHashMap<>();

    public DailyRewardExpansion(DailyRewardPlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }
    @Override public @NotNull String getIdentifier() { return identifier; }
    @Override public @NotNull String getAuthor() { return "Alvran"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return false; }

    @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();
        return switch (key) {
            case "daily_state" -> {
                String rewardId = plugin.getCatalog().daily().keySet().stream().findFirst().orElse("daily");
                yield hasClaim(uuid, rewardId, "DAILY", currentPeriod()) ? "CLAIMED" : "AVAILABLE";
            }
            case "daily_next_reset" -> DateUtil.format(DateUtil.nextReset(Instant.now(), zone(), resetTime()), zone(),
                    plugin.getConfig().getString("time.date-format", "dd MMM yyyy HH:mm"));
            case "streak_current" -> String.valueOf(stats(uuid).currentStreak());
            case "streak_best" -> String.valueOf(stats(uuid).bestStreak());
            case "playtime_total_minutes" -> String.valueOf(plugin.getPlaytimeService().cachedMinutes(uuid));
            case "event_available" -> String.valueOf(plugin.getCatalog().events().values().stream()
                    .anyMatch(r -> activeEvent(uuid, r.id())));
            case "onetime_available" -> String.valueOf(plugin.getCatalog().oneTime().values().stream()
                    .anyMatch(r -> !hasClaim(uuid, r.id(), "ONETIME", "once")));
            default -> key.startsWith("playtime_progress_") ? playtimeProgress(uuid, key.substring("playtime_progress_".length())) : null;
        };
    }

    public void invalidate(UUID uuid) { stateCache.keySet().removeIf(key -> key.startsWith(uuid + ":")); }
    public void invalidateAll() { stateCache.clear(); }

    private boolean hasClaim(UUID uuid, String rewardId, String type, String period) {
        String cacheKey = uuid + ":claim:" + rewardId + ":" + type + ":" + period;
        return stateCache.computeIfAbsent(cacheKey, k -> plugin.getDatabase().hasClaim(uuid, rewardId, type, period)).getNow(false);
    }

    private boolean activeEvent(UUID uuid, String rewardId) {
        String cacheKey = uuid + ":event:" + rewardId;
        return stateCache.computeIfAbsent(cacheKey, k -> plugin.getDatabase().hasActiveEvent(rewardId, uuid)).getNow(false);
    }

    private String playtimeProgress(UUID uuid, String rewardId) {
        var reward = plugin.getCatalog().playtime().get(rewardId);
        if (reward == null) return "";
        long minutes = plugin.getPlaytimeService().cachedMinutes(uuid);
        long required = Math.max(0, reward.requiredMinutes());
        if (required == 0) return "100";
        return String.valueOf(Math.min(100, (minutes * 100) / required));
    }

    private PlayerStats stats(UUID uuid) { return plugin.getPlaytimeService().cachedStats(uuid); }
    private ZoneId zone() { try { return ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); } catch (Exception e) { return ZoneId.of("Asia/Jakarta"); } }
    private LocalTime resetTime() { try { return LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00")); } catch (Exception e) { return LocalTime.MIDNIGHT; } }
    private String currentPeriod() { return DateUtil.periodDate(Instant.now(), zone(), resetTime()).toString(); }
}
