package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.db.DatabaseManager;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.PlaytimeMath;
import org.bukkit.Statistic;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reads the exact Bukkit playtime statistic used by EssentialsX /playtime.
 * PLAY_ONE_MINUTE is the modern Bukkit name for the historical PLAY_ONE_TICK counter;
 * its value is the player's accumulated play ticks, not minutes.
 */
public final class PlaytimeService {
    private static final int MAX_CACHED_PLAYER_RECORDS = 4096;
    private final DailyRewardPlugin plugin;
    private final DatabaseManager database;
    private final Map<UUID, AtomicLong> cachedTotal = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> cachedStats = new ConcurrentHashMap<>();

    public PlaytimeService(DailyRewardPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void join(Player player) {
        refresh(player);
        UUID uuid = player.getUniqueId();
        database.getStats(uuid).thenAccept(stats -> {
            cachedStats.put(uuid, new PlayerStats(cachedMillis(uuid), stats.currentStreak(), stats.bestStreak(), stats.lastDailyPeriod()));
            trimOfflineCaches();
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not load player reward stats for " + player.getName() + ": " + error.getMessage());
            return null;
        });
    }

    public void sampleOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
    }

    public void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        long playtimeMillis = statisticMillis(player);
        cachedTotal.computeIfAbsent(uuid, ignored -> new AtomicLong()).set(playtimeMillis);
        PlayerStats previous = cachedStats(uuid);
        cachedStats.put(uuid, new PlayerStats(playtimeMillis, previous.currentStreak(), previous.bestStreak(), previous.lastDailyPeriod()));
        database.setPlaytimeMillis(uuid, playtimeMillis).exceptionally(error -> {
            plugin.getLogger().warning("Could not sync EssentialsX playtime for " + player.getName() + ": " + error.getMessage());
            return null;
        });
        trimOfflineCaches();
    }

    public void quit(Player player) {
        refresh(player);
        trimOfflineCaches();
    }

    private long statisticMillis(Player player) {
        // EssentialsX 2.x Commandplaytime reads this same statistic for online players.
        return PlaytimeMath.ticksToMillis(player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    /** Keep offline placeholder/stat snapshots bounded without evicting online players. */
    private void trimOfflineCaches() {
        if (cachedTotal.size() <= MAX_CACHED_PLAYER_RECORDS && cachedStats.size() <= MAX_CACHED_PLAYER_RECORDS) return;
        for (UUID id : cachedTotal.keySet()) {
            if (plugin.getServer().getPlayer(id) == null) {
                cachedTotal.remove(id);
                cachedStats.remove(id);
                if (cachedTotal.size() <= MAX_CACHED_PLAYER_RECORDS && cachedStats.size() <= MAX_CACHED_PLAYER_RECORDS) return;
            }
        }
        for (UUID id : cachedStats.keySet()) {
            if (plugin.getServer().getPlayer(id) == null) {
                cachedStats.remove(id);
                if (cachedTotal.size() <= MAX_CACHED_PLAYER_RECORDS && cachedStats.size() <= MAX_CACHED_PLAYER_RECORDS) return;
            }
        }
    }

    public void flushAll() {
        sampleOnline();
    }

    public long cachedMillis(UUID uuid) {
        AtomicLong total = cachedTotal.get(uuid);
        return total == null ? cachedStats.getOrDefault(uuid, PlayerStats.empty()).playtimeMillis() : total.get();
    }

    public long cachedMinutes(UUID uuid) { return cachedMillis(uuid) / 60_000L; }

    /** Reads EssentialsX's underlying statistic directly for an online player. */
    public long minutesForOnline(Player player) {
        return PlaytimeMath.ticksToMinutes(player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    /** Offline-safe cached view; never reads Bukkit state or performs database I/O. */
    public long cachedMinutes(OfflinePlayer player) { return cachedMinutes(player.getUniqueId()); }

    public PlayerStats cachedStats(UUID uuid) {
        return cachedStats.getOrDefault(uuid, PlayerStats.empty());
    }

    public void updateStreakCache(UUID uuid, int current, int best) {
        PlayerStats old = cachedStats(uuid);
        cachedStats.put(uuid, new PlayerStats(cachedMillis(uuid), current, best, old.lastDailyPeriod()));
    }

    /** Refreshes the display cache only; actual reward eligibility always reads EssentialsX's statistic. */
    public void setCached(UUID uuid, long minutes) {
        long millis = Math.max(0, minutes) * 60_000L;
        cachedTotal.computeIfAbsent(uuid, ignored -> new AtomicLong()).set(millis);
        PlayerStats old = cachedStats(uuid);
        cachedStats.put(uuid, new PlayerStats(millis, old.currentStreak(), old.bestStreak(), old.lastDailyPeriod()));
    }
}
