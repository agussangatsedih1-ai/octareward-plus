package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.api.DailyRewardPreClaimEvent;
import id.tempestaria.dailyreward.api.DailyRewardClaimedEvent;
import id.tempestaria.dailyreward.api.DailyRewardStreakChangeEvent;
import id.tempestaria.dailyreward.db.ClaimResult;
import id.tempestaria.dailyreward.db.DatabaseManager;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.DateUtil;
import id.tempestaria.dailyreward.util.RewardPeriodUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ClaimService {
    private final DailyRewardPlugin plugin;
    private final DatabaseManager database;

    public ClaimService(DailyRewardPlugin plugin, DatabaseManager database) { this.plugin = plugin; this.database = database; }

    public void claim(Player player, String category, String rewardId) {
        Optional<RewardDefinition> opt = findByCategory(category, rewardId);
        if (opt.isEmpty()) { plugin.message(player, "unknown-reward"); return; }
        RewardDefinition reward = opt.get();
        if (!reward.enabled()) { plugin.message(player, "claim-locked"); return; }
        if (!reward.permission().isBlank() && !player.hasPermission(reward.permission())) { plugin.message(player, "claim-locked"); return; }
        if (!withinWindow(reward.startsAt(), reward.endsAt())) { plugin.message(player, "claim-locked"); return; }
        DailyRewardPreClaimEvent preEvent = new DailyRewardPreClaimEvent(player, category.toLowerCase(Locale.ROOT), reward.id());
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) { plugin.message(player, "claim-locked"); return; }
        switch (category.toLowerCase(Locale.ROOT)) {
            case "daily" -> claimDaily(player, reward);
            case "weekly", "monthly" -> claimPeriodic(player, reward, category.toUpperCase(Locale.ROOT));
            case "playtime" -> claimPlaytime(player, reward);
            case "events" -> claimEvent(player, reward);
            case "onetime" -> claimOneTime(player, reward, false);
            case "supporter" -> claimOneTime(player, reward, true);
            default -> plugin.message(player, "claim-locked");
        }
    }

    private Optional<RewardDefinition> findByCategory(String category, String id) {
        Map<String, RewardDefinition> map = switch (category.toLowerCase(Locale.ROOT)) {
            case "daily" -> plugin.getCatalog().daily();
            case "weekly" -> plugin.getCatalog().weekly();
            case "monthly" -> plugin.getCatalog().monthly();
            case "playtime" -> plugin.getCatalog().playtime();
            case "events" -> plugin.getCatalog().events();
            case "onetime" -> plugin.getCatalog().oneTime();
            case "supporter" -> plugin.getCatalog().supporter();
            default -> Map.of();
        };
        return map.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(id)).map(Map.Entry::getValue).findFirst();
    }

    private void claimDaily(Player player, RewardDefinition reward) {
        ZoneId zone = zone(); LocalTime reset = resetTime();
        LocalDate period = DateUtil.periodDate(Instant.now(), zone, reset);
        database.claimDaily(player.getUniqueId(), reward.id(), period, plugin.getCatalog().missedDayPolicy(), plugin.getCatalog().graceDays(), plugin.getCatalog().streak().keySet())
                .whenComplete((result, error) -> onMain(() -> {
                    if (error != null) { logFailure("daily", error); plugin.message(player, "claim-error"); return; }
                    if (!result.success()) { plugin.message(player, result.duplicate() ? "claim-duplicate" : "claim-error"); return; }
                    if (!player.isOnline()) { plugin.getLogger().warning("Daily claim committed for an offline player; action ledger remains PENDING for manual review: " + player.getUniqueId()); return; }
                    Map<String, String> extra = Map.of("streak", String.valueOf(result.currentStreak()));
                    boolean actionsOk = plugin.getActionService().execute(player, reward, extra);
                    RewardDefinition milestone = plugin.getCatalog().streak().get(result.currentStreak());
                    if (milestone != null && result.streakMilestone()) {
                        boolean milestoneOk = plugin.getActionService().execute(player, milestone, extra);
                        actionsOk &= milestoneOk;
                        if (milestoneOk) database.markClaimStatus(player.getUniqueId(), milestone.id(), "STREAK", period.toString(), "DONE");
                    }
                    database.markClaimStatus(player.getUniqueId(), reward.id(), "DAILY", period.toString(), actionsOk ? "DONE" : "PENDING");
                    if (!actionsOk) plugin.getLogger().warning("Daily claim for " + player.getName() + " is committed but has action failures; it will not auto-run again.");
                    plugin.getPlaytimeService().updateStreakCache(player.getUniqueId(), result.currentStreak(), result.bestStreak());
                    plugin.invalidatePlaceholderState(player.getUniqueId());
                    Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, "daily", reward.id()));
                    if (result.previousStreak() != result.currentStreak()) Bukkit.getPluginManager().callEvent(new DailyRewardStreakChangeEvent(player, result.previousStreak(), result.currentStreak()));
                    plugin.message(player, "claim-success");
                    plugin.getGuiManager().openCategory(player, "daily");
                }));
    }

    private void claimPeriodic(Player player, RewardDefinition reward, String type) {
        ZoneId zone = zone();
        String category = type.toLowerCase(Locale.ROOT);
        String period = RewardPeriodUtil.key(Instant.now(), zone, resetTime(), category);
        database.claimOnce(player.getUniqueId(), reward.id(), type, period).whenComplete((ok, error) -> onMain(() -> {
            if (error != null) { logFailure(category + " claim", error); plugin.message(player, "claim-error"); return; }
            if (!ok) { plugin.message(player, "claim-duplicate"); return; }
            if (!player.isOnline()) {
                plugin.getLogger().warning(category + " claim committed for offline player; reward actions require manual review: " + player.getUniqueId());
                return;
            }
            boolean actionsOk = plugin.getActionService().execute(player, reward, Map.of());
            database.markClaimStatus(player.getUniqueId(), reward.id(), type, period, actionsOk ? "DONE" : "PENDING");
            Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, category, reward.id()));
            plugin.invalidatePlaceholderState(player.getUniqueId());
            plugin.message(player, "claim-success");
            plugin.getGuiManager().openCategory(player, category);
        }));
    }

    private void claimPlaytime(Player player, RewardDefinition reward) {
        onMain(() -> {
            long minutes = plugin.getPlaytimeService().minutesForOnline(player);
            if (!player.hasPermission("dailyreward.playtime.bypass") && minutes < reward.requiredMinutes()) { plugin.message(player, "claim-locked"); return; }
            String period = "once";
            if (reward.repeat()) {
                long interval = reward.repeatIntervalMinutes() > 0 ? reward.repeatIntervalMinutes() : Math.max(1, reward.requiredMinutes());
                period = "interval-" + (minutes / interval);
            }
            final String claimPeriod = period;
            database.claimOnce(player.getUniqueId(), reward.id(), "PLAYTIME", claimPeriod).whenComplete((ok, ex) -> onMain(() -> {
                if (ex != null) { logFailure("playtime claim", ex); plugin.message(player, "claim-error"); return; }
                if (!ok) { plugin.message(player, "claim-duplicate"); return; }
                if (!player.isOnline()) { plugin.getLogger().warning("Playtime claim committed for offline player; actions are PENDING: " + player.getUniqueId()); return; }
                boolean actionsOk = plugin.getActionService().execute(player, reward, Map.of("playtime", String.valueOf(minutes)));
                database.markClaimStatus(player.getUniqueId(), reward.id(), "PLAYTIME", claimPeriod, actionsOk ? "DONE" : "PENDING");
                Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, "playtime", reward.id()));
                plugin.invalidatePlaceholderState(player.getUniqueId());
                plugin.message(player, "claim-success"); plugin.getGuiManager().openCategory(player, "playtime");
            }));
        });
    }

    private void claimOneTime(Player player, RewardDefinition reward, boolean supporter) {
        if (reward.firstJoinOnly() && player.hasPlayedBefore()) { plugin.message(player, "claim-locked"); return; }
        if (!withinWindow(reward.startsAt(), reward.endsAt())) { plugin.message(player, "claim-locked"); return; }
        String claimType = supporter ? "SUPPORTER" : "ONETIME";
        database.claimOnce(player.getUniqueId(), reward.id(), claimType, "once").whenComplete((ok, error) -> onMain(() -> {
            if (error != null) { logFailure("one-time claim", error); plugin.message(player, "claim-error"); return; }
            if (!ok) { plugin.message(player, "claim-duplicate"); return; }
            if (!player.isOnline()) { plugin.getLogger().warning("One-time claim committed for offline player; actions are PENDING: " + player.getUniqueId()); return; }
            boolean actionsOk = plugin.getActionService().execute(player, reward, Map.of());
            database.markClaimStatus(player.getUniqueId(), reward.id(), claimType, "once", actionsOk ? "DONE" : "PENDING");
            Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, "onetime", reward.id()));
            plugin.invalidatePlaceholderState(player.getUniqueId());
            plugin.message(player, "claim-success"); plugin.getGuiManager().openCategory(player, claimType.equals("SUPPORTER") ? "supporter" : "onetime");
        }));
    }

    private void claimEvent(Player player, RewardDefinition reward) {
        if (!withinWindow(reward.startsAt(), reward.endsAt())) { plugin.message(player, "claim-locked"); return; }
        database.claimEvent(player.getUniqueId(), reward.id(), reward.claimLimit()).whenComplete((instance, error) -> onMain(() -> {
            if (error != null) { logFailure("event claim", error); plugin.message(player, "claim-error"); return; }
            if (instance == null) { plugin.message(player, "claim-locked"); return; }
            if (!player.isOnline()) { plugin.getLogger().warning("Event claim committed for offline player; reward actions require manual review: " + player.getUniqueId()); return; }
            boolean actionsOk = plugin.getActionService().execute(player, reward, Map.of("event_id", reward.id()));
            database.markClaimStatus(player.getUniqueId(), reward.id(), "EVENT", instance, actionsOk ? "DONE" : "PENDING");
            if (!actionsOk) plugin.getLogger().warning("Event claim for " + player.getName() + " has action failures and requires manual review.");
            Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, "events", reward.id()));
            plugin.invalidatePlaceholderState(player.getUniqueId());
            plugin.message(player, "claim-success"); plugin.getGuiManager().openCategory(player, "events");
        }));
    }

    /** Called by Bukkit event adapters for configured BLOCK_BREAK, ENTITY_KILL or LEVEL_UP triggers. */
    public void onBukkitTrigger(Player player, String triggerType, String target, String world) {
        for (RewardDefinition reward : plugin.getCatalog().events().values()) {
            if (!reward.enabled() || !reward.triggerType().equalsIgnoreCase(triggerType)) continue;
            if (!reward.triggerTarget().isBlank() && !reward.triggerTarget().equalsIgnoreCase(target)) continue;
            if (!reward.triggerWorld().isBlank() && !reward.triggerWorld().equalsIgnoreCase(world)) continue;
            if (!withinWindow(reward.startsAt(), reward.endsAt())) continue;
            database.incrementEventProgress(player.getUniqueId(), reward.id(), reward.triggerRequiredCount())
                    .thenAccept(reached -> {
                        if (!reached) return;
                        long now = System.currentTimeMillis();
                        long start = parseEpoch(reward.startsAt(), now);
                        long end = parseEpoch(reward.endsAt(), now + 86_400_000L);
                        database.triggerEvent(reward.id(), player.getUniqueId(), start, end).thenAccept(instance -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                Bukkit.getPluginManager().callEvent(new id.tempestaria.dailyreward.api.DailyRewardEventTriggeredEvent(reward.id(), instance, player));
                                plugin.invalidatePlaceholderState(player.getUniqueId());
                                player.sendMessage(ActionService.component("&6Event unlocked: &f" + reward.displayName()));
                            });
                            database.audit(null, player.getUniqueId(), "Bukkit_EVENT_TRIGGER", triggerType + ":" + reward.id());
                        });
                    }).exceptionally(ex -> { plugin.getLogger().warning("Event trigger progress failed for " + reward.id() + ": " + ex.getMessage()); return null; });
        }
    }

    private long parseEpoch(String value, long fallback) {
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); }
        catch (Exception ignored) { return fallback; }
    }

    public void grantByAdmin(Player actor, Player target, String rewardId) {
        Optional<RewardDefinition> reward = plugin.getCatalog().find(rewardId);
        if (reward.isEmpty()) { if (actor != null) plugin.message(actor, "unknown-reward"); return; }
        boolean ok = plugin.getActionService().execute(target, reward.get(), Map.of());
        database.audit(actor == null ? null : actor.getUniqueId(), target.getUniqueId(), "ADMIN_GIVE", rewardId);
        if (actor != null) actor.sendMessage(DailyRewardPlugin.color("&aGranted &f" + rewardId + " &ato " + target.getName() + (ok ? "." : " (some actions failed).")));
    }

    public void reset(Player actor, UUID target, String rewardId) {
        database.resetClaim(target, rewardId).thenRun(() -> {
            plugin.invalidatePlaceholderState(target);
            database.audit(actor == null ? null : actor.getUniqueId(), target, "ADMIN_RESET", rewardId);
        });
    }

    private boolean withinWindow(String start, String end) {
        Instant now = Instant.now();
        try { if (start != null && !start.isBlank() && now.isBefore(OffsetDateTime.parse(start).toInstant())) return false; }
        catch (DateTimeParseException ex) { plugin.getLogger().warning("Invalid reward starts-at timestamp: " + start); return false; }
        try { if (end != null && !end.isBlank() && now.isAfter(OffsetDateTime.parse(end).toInstant())) return false; }
        catch (DateTimeParseException ex) { plugin.getLogger().warning("Invalid reward ends-at timestamp: " + end); return false; }
        return true;
    }

    private ZoneId zone() { try { return ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); } catch (Exception ex) { return ZoneId.of("Asia/Jakarta"); } }
    private LocalTime resetTime() { try { return LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00")); } catch (Exception ex) { return LocalTime.MIDNIGHT; } }
    private void onMain(Runnable runnable) { Bukkit.getScheduler().runTask(plugin, runnable); }
    private void logFailure(String operation, Throwable error) { plugin.getLogger().warning("Failed " + operation + ": " + error.getMessage()); }
}
