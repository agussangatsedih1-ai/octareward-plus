package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.util.DateUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One shared timer checks online players; no per-player repeating tasks are created. */
public final class ReminderService {
    private final DailyRewardPlugin plugin;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    public ReminderService(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L * 60L, 20L * 60L);
    }

    private void check() {
        if (!plugin.getConfig().getBoolean("reminders.enabled", false)) return;
        RewardDefinition daily = plugin.getCatalog().daily().values().stream().filter(RewardDefinition::enabled).findFirst().orElse(null);
        if (daily == null) return;
        long now = System.currentTimeMillis();
        long interval = Math.max(1, plugin.getConfig().getLong("reminders.interval-minutes", 60)) * 60_000L;
        String period = DateUtil.periodDate(Instant.ofEpochMilli(now), zone(), reset()).toString();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long last = lastSent.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < interval) continue;
            plugin.getDatabase().hasClaim(player.getUniqueId(), daily.id(), "DAILY", period).thenAccept(claimed -> {
                if (claimed) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    long sentAt = System.currentTimeMillis();
                    if (sentAt - lastSent.getOrDefault(player.getUniqueId(), 0L) < interval) return;
                    lastSent.put(player.getUniqueId(), sentAt);
                    plugin.message(player, "daily-reminder");
                });
            }).exceptionally(ex -> { plugin.getLogger().fine("Daily reminder status lookup failed: " + ex.getMessage()); return null; });
        }
    }

    private ZoneId zone() { try { return ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); } catch (Exception e) { return ZoneId.of("Asia/Jakarta"); } }
    private LocalTime reset() { try { return LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00")); } catch (Exception e) { return LocalTime.MIDNIGHT; } }
}
