package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.db.DatabaseManager;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.PlaytimeMath;
import org.bukkit.Statistic;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlaytimeService {
    private final DailyRewardPlugin plugin;
    private final DatabaseManager database;
    private final Map<UUID, Long> lastSample = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> cachedTotal = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> cachedStats = new ConcurrentHashMap<>();
    private volatile Set<String> excludedWorlds = Set.of();

    public PlaytimeService(DailyRewardPlugin plugin, DatabaseManager database) {
        this.plugin = plugin; this.database = database; reloadSettings();
    }

    public void reloadSettings() {
        excludedWorlds = Set.copyOf(plugin.getConfig().getStringList("playtime.excluded-worlds"));
    }

    public void join(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastSample.put(id, now); lastActivity.put(id, now);
        boolean useStatistic = plugin.getConfig().getString("playtime.source", "DAILYREWARD_TRACKER").equalsIgnoreCase("MINECRAFT_STATISTIC");
        if (useStatistic) cachedTotal.computeIfAbsent(id, k -> new AtomicLong()).set(PlaytimeMath.ticksToMillis(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        database.getStats(id).thenAccept(stats -> {
            cachedStats.put(id, stats);
            if (!useStatistic) cachedTotal.computeIfAbsent(id, k -> new AtomicLong()).set(stats.playtimeMillis());
        }).exceptionally(ex -> { plugin.getLogger().warning("Could not load playtime for " + player.getName() + ": " + ex.getMessage()); return null; });
    }

    public void activity(Player player) { lastActivity.put(player.getUniqueId(), System.currentTimeMillis()); }

    public void sampleOnline() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) sample(player, now);
    }

    public void sample(Player player, long now) {
        UUID id = player.getUniqueId();
        Long previous = lastSample.put(id, now);
        if (previous == null) return;
        long delta = Math.max(0, now - previous);
        if (delta == 0 || excludedWorlds.contains(player.getWorld().getName())) return;
        boolean countAfk = plugin.getConfig().getBoolean("playtime.count-afk", false);
        long afkLimit = plugin.getConfig().getLong("playtime.afk-idle-minutes", 5) * 60_000L;
        long lastMove = lastActivity.getOrDefault(id, now);
        boolean afk = now - lastMove >= afkLimit;
        if (!countAfk && afk) return;
        boolean useStatistic = plugin.getConfig().getString("playtime.source", "DAILYREWARD_TRACKER").equalsIgnoreCase("MINECRAFT_STATISTIC");
        if (useStatistic) cachedTotal.computeIfAbsent(id, k -> new AtomicLong()).set(PlaytimeMath.ticksToMillis(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        else cachedTotal.computeIfAbsent(id, k -> new AtomicLong()).addAndGet(delta);
        database.addPlaytime(id, delta).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to save playtime for " + player.getName() + ": " + ex.getMessage()); return null;
        });
    }

    public void quit(Player player) {
        sample(player, System.currentTimeMillis());
        UUID id = player.getUniqueId(); lastSample.remove(id); lastActivity.remove(id);
    }

    public void flushAll() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) sample(player, now);
    }

    public long cachedMillis(UUID uuid) { return cachedTotal.getOrDefault(uuid, new AtomicLong()).get(); }
    public long cachedMinutes(UUID uuid) { return cachedMillis(uuid) / 60_000L; }
    /** Call on the server thread when player is online. */
    public long minutesForOnline(Player player) {
        String source = plugin.getConfig().getString("playtime.source", "DAILYREWARD_TRACKER").toUpperCase();
        if (source.equals("MINECRAFT_STATISTIC")) return Math.max(0, PlaytimeMath.ticksToMinutes(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
        return cachedMinutes(player.getUniqueId());
    }
    /** Offline-safe cached view; never reads Bukkit state or performs database I/O. */
    public long cachedMinutes(OfflinePlayer player) { return cachedMinutes(player.getUniqueId()); }
    public PlayerStats cachedStats(UUID uuid) { return cachedStats.getOrDefault(uuid, PlayerStats.empty()); }
    public void updateStreakCache(UUID uuid, int current, int best) {
        PlayerStats old = cachedStats(uuid);
        cachedStats.put(uuid, new PlayerStats(cachedMillis(uuid), current, best, old.lastDailyPeriod()));
    }
    public void setCached(UUID uuid, long minutes) {
        long ms = Math.max(0, minutes) * 60_000L;
        cachedTotal.computeIfAbsent(uuid, k -> new AtomicLong()).set(ms);
        PlayerStats old = cachedStats(uuid);
        cachedStats.put(uuid, new PlayerStats(ms, old.currentStreak(), old.bestStreak(), old.lastDailyPeriod()));
    }
}
