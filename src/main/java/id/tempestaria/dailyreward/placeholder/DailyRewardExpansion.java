package id.tempestaria.dailyreward.placeholder;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.db.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** PlaceholderAPI expansion. Requests never block the server thread on database I/O. */
public final class DailyRewardExpansion extends PlaceholderExpansion {
    private final DailyRewardPlugin plugin;
    private final String identifier;
    private final Map<String, CompletableFuture<Boolean>> stateCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Long>> cooldownCache = new ConcurrentHashMap<>();

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
        Player online = player.getPlayer();
        return switch (key) {
            case "daily_state" -> stateForFirst(uuid, "daily", online);
            case "weekly_state" -> stateForFirst(uuid, "weekly", online);
            case "monthly_state" -> stateForFirst(uuid, "monthly", online);
            case "daily_claimed" -> String.valueOf(isFirstRewardClaimed(uuid, "daily"));
            case "weekly_claimed" -> String.valueOf(isFirstRewardClaimed(uuid, "weekly"));
            case "monthly_claimed" -> String.valueOf(isFirstRewardClaimed(uuid, "monthly"));
            case "daily_available" -> String.valueOf(isFirstRewardAvailable(uuid, "daily", online));
            case "weekly_available" -> String.valueOf(isFirstRewardAvailable(uuid, "weekly", online));
            case "monthly_available" -> String.valueOf(isFirstRewardAvailable(uuid, "monthly", online));
            case "daily_cooldown" -> cooldownText(uuid, "daily");
            case "weekly_cooldown" -> cooldownText(uuid, "weekly");
            case "monthly_cooldown" -> cooldownText(uuid, "monthly");
            case "daily_next_reset" -> cooldownDate(uuid, "daily");
            case "weekly_next_reset" -> cooldownDate(uuid, "weekly");
            case "monthly_next_reset" -> cooldownDate(uuid, "monthly");
            case "streak_current" -> String.valueOf(stats(uuid).currentStreak());
            case "streak_best" -> String.valueOf(stats(uuid).bestStreak());
            case "streak_next_milestone" -> String.valueOf(nextMilestone(stats(uuid).currentStreak()));
            case "streak_progress" -> String.valueOf(streakProgress(stats(uuid).currentStreak()));
            case "playtime_total_minutes" -> String.valueOf(plugin.getPlaytimeService().cachedMinutes(uuid));
            case "playtime_formatted" -> formatMinutes(plugin.getPlaytimeService().cachedMinutes(uuid));
            case "event_available" -> String.valueOf(plugin.getCatalog().events().values().stream()
                    .anyMatch(r -> r.enabled() && withinWindow(r) && activeEvent(uuid, r.id())));
            case "event_active_count" -> String.valueOf(plugin.getCatalog().events().values().stream()
                    .filter(r -> r.enabled() && withinWindow(r) && activeEvent(uuid, r.id())).count());
            case "onetime_available" -> String.valueOf(plugin.getCatalog().oneTime().values().stream().anyMatch(r ->
                    r.enabled() && withinWindow(r) && (online == null || r.permission().isBlank() || online.hasPermission(r.permission()))
                            && !hasClaim(uuid, r.id(), "ONETIME", "once")));
            default -> resolveDynamic(uuid, online, key);
        };
    }

    private String resolveDynamic(UUID uuid, Player online, String key) {
        if (key.startsWith("playtime_progress_")) return playtimeProgress(uuid, key.substring("playtime_progress_".length()));
        if (key.startsWith("event_available_")) {
            String id = key.substring("event_available_".length());
            Optional<RewardDefinition> event = plugin.getCatalog().findEvent(id);
            return String.valueOf(event.isPresent() && event.get().enabled() && withinWindow(event.get())
                    && activeEvent(uuid, event.get().id()));
        }
        if (key.startsWith("reward_claimed_")) {
            String[] parts = key.substring("reward_claimed_".length()).split("_", 2);
            if (parts.length == 2) return String.valueOf(isRewardClaimed(uuid, parts[0], parts[1]));
        }
        if (key.startsWith("reward_state_")) {
            String[] parts = key.substring("reward_state_".length()).split("_", 2);
            if (parts.length == 2) {
                Optional<RewardDefinition> reward = findReward(parts[0], parts[1]);
                if (reward.isEmpty() || !reward.get().enabled() || !withinWindow(reward.get())) return "LOCKED";
                if (Set.of("daily", "weekly", "monthly").contains(parts[0]) && !plugin.isPeriodicRewardEnabled(parts[0])) return "LOCKED";
                if (isRewardClaimed(uuid, parts[0], parts[1])) return Set.of("daily", "weekly", "monthly").contains(parts[0]) ? "COOLDOWN" : "CLAIMED";
                if (online != null && !reward.get().permission().isBlank() && !online.hasPermission(reward.get().permission())) return "LOCKED";
                if (parts[0].equals("playtime") && plugin.getPlaytimeService().cachedMinutes(uuid) < reward.get().requiredMinutes()) return "LOCKED";
                if (parts[0].equals("streak") && stats(uuid).currentStreak() < milestoneDay(reward.get().id())) return "LOCKED";
                if (parts[0].equals("events") && !activeEvent(uuid, reward.get().id())) return "LOCKED";
                return "AVAILABLE";
            }
        }
        if (key.startsWith("reward_available_")) {
            String[] parts = key.substring("reward_available_".length()).split("_", 2);
            if (parts.length == 2) {
                Optional<RewardDefinition> reward = findReward(parts[0], parts[1]);
                if (reward.isEmpty() || !reward.get().enabled() || !withinWindow(reward.get())) return "false";
                if (Set.of("daily", "weekly", "monthly").contains(parts[0]) && !plugin.isPeriodicRewardEnabled(parts[0])) return "false";
                if (online != null && !reward.get().permission().isBlank() && !online.hasPermission(reward.get().permission())) return "false";
                if (parts[0].equals("playtime") && plugin.getPlaytimeService().cachedMinutes(uuid) < reward.get().requiredMinutes()) return "false";
                if (parts[0].equals("streak") && stats(uuid).currentStreak() < milestoneDay(reward.get().id())) return "false";
                if (parts[0].equals("events") && !activeEvent(uuid, reward.get().id())) return "false";
                return String.valueOf(!isRewardClaimed(uuid, parts[0], parts[1]));
            }
        }
        if (key.startsWith("event_claimed_")) {
            String id = key.substring("event_claimed_".length());
            Optional<RewardDefinition> event = plugin.getCatalog().findEvent(id);
            return String.valueOf(event.isPresent() && isRewardClaimed(uuid, "events", event.get().id()));
        }
        if (key.startsWith("playtime_claimed_")) return String.valueOf(isRewardClaimed(uuid, "playtime", key.substring("playtime_claimed_".length())));
        return null;
    }

    private boolean isFirstRewardClaimed(UUID uuid, String category) {
        String id = plugin.getCatalog().allForCategory(category).stream().findFirst().map(RewardDefinition::id).orElse("");
        return !id.isBlank() && isRewardClaimed(uuid, category, id);
    }

    private String stateForFirst(UUID uuid, String category, Player online) {
        RewardDefinition reward = firstReward(category).orElse(null);
        if (reward == null || !reward.enabled() || !withinWindow(reward)) return "LOCKED";
        if (Set.of("daily", "weekly", "monthly").contains(category) && !plugin.isPeriodicRewardEnabled(category)) return "LOCKED";
        if (isRewardClaimed(uuid, category, reward.id())) return Set.of("daily", "weekly", "monthly").contains(category) ? "COOLDOWN" : "CLAIMED";
        if (online != null && !reward.permission().isBlank() && !online.hasPermission(reward.permission())) return "LOCKED";
        return "AVAILABLE";
    }

    private boolean isFirstRewardAvailable(UUID uuid, String category, Player online) {
        RewardDefinition reward = firstReward(category).orElse(null);
        if (reward == null || !reward.enabled() || !withinWindow(reward)) return false;
        if (Set.of("daily", "weekly", "monthly").contains(category) && !plugin.isPeriodicRewardEnabled(category)) return false;
        if (online != null && !reward.permission().isBlank() && !online.hasPermission(reward.permission())) return false;
        return !isRewardClaimed(uuid, category, reward.id());
    }

    private Optional<RewardDefinition> firstReward(String category) {
        return plugin.getCatalog().allForCategory(category).stream().findFirst();
    }

    private boolean isRewardClaimed(UUID uuid, String category, String id) {
        Optional<RewardDefinition> reward = findReward(category, id);
        if (reward.isEmpty()) return false;
        String type;
        String period;
        switch (category) {
            case "daily", "weekly", "monthly" -> { return isCoolingDown(uuid, category); }
            case "playtime" -> {
                type = "PLAYTIME";
                long minutes = plugin.getPlaytimeService().cachedMinutes(uuid);
                period = reward.get().repeat() ? "interval-" + (minutes / Math.max(1, reward.get().repeatIntervalMinutes() > 0 ? reward.get().repeatIntervalMinutes() : reward.get().requiredMinutes())) : "once";
            }
            case "events" -> {
                int limit = reward.get().claimLimit();
                return state(uuid, "event-limit:" + id + ":" + limit,
                        () -> plugin.getDatabase().hasReachedClaimLimit(uuid, id, "EVENT", limit));
            }
            case "onetime" -> { type = "ONETIME"; period = "once"; }
            case "supporter" -> { type = "SUPPORTER"; period = "once"; }
            case "streak" -> { return state(uuid, "streak:" + id, () -> plugin.getDatabase().hasAnyClaim(uuid, id, "STREAK")); }
            default -> { type = "DAILY"; period = "once"; }
        }
        return hasClaim(uuid, id, type, period);
    }

    private boolean hasClaim(UUID uuid, String id, String type, String period) {
        return state(uuid, "claim:" + id + ":" + type + ":" + period,
                () -> plugin.getDatabase().hasClaim(uuid, id, type, period));
    }

    private boolean activeEvent(UUID uuid, String id) {
        return state(uuid, "event:" + id, () -> plugin.getDatabase().hasActiveEvent(id, uuid));
    }

    private boolean state(UUID uuid, String suffix, java.util.function.Supplier<CompletableFuture<Boolean>> query) {
        // Keep placeholder cache growth bounded on busy servers with many unique players/reward IDs.
        if (stateCache.size() > 8192) stateCache.clear();
        String cacheKey = uuid + ":" + suffix;
        CompletableFuture<Boolean> future = stateCache.computeIfAbsent(cacheKey, ignored -> query.get());
        if (!future.isDone()) return false;
        try {
            return future.getNow(false);
        } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException ex) {
            stateCache.remove(cacheKey, future);
            return false;
        }
    }

    private Optional<RewardDefinition> findReward(String category, String id) {
        Map<String, RewardDefinition> rewards = switch (category) {
            case "daily" -> plugin.getCatalog().daily();
            case "weekly" -> plugin.getCatalog().weekly();
            case "monthly" -> plugin.getCatalog().monthly();
            case "playtime" -> plugin.getCatalog().playtime();
            case "events" -> plugin.getCatalog().events();
            case "onetime" -> plugin.getCatalog().oneTime();
            case "supporter" -> plugin.getCatalog().supporter();
            case "streak" -> {
                Map<String, RewardDefinition> values = new LinkedHashMap<>();
                plugin.getCatalog().streak().values().forEach(reward -> values.put(reward.id(), reward));
                yield values;
            }
            default -> Map.of();
        };
        return rewards.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(id))
                .map(Map.Entry::getValue).findFirst();
    }

    private String playtimeProgress(UUID uuid, String rewardId) {
        RewardDefinition reward = findReward("playtime", rewardId).orElse(null);
        if (reward == null) return "";
        long minutes = plugin.getPlaytimeService().cachedMinutes(uuid);
        long required = Math.max(0, reward.requiredMinutes());
        return required == 0 ? "100" : String.valueOf(Math.min(100, minutes * 100 / required));
    }

    private boolean withinWindow(RewardDefinition reward) {
        Instant now = Instant.now();
        try {
            if (!reward.startsAt().isBlank() && now.isBefore(OffsetDateTime.parse(reward.startsAt()).toInstant())) return false;
            if (!reward.endsAt().isBlank() && now.isAfter(OffsetDateTime.parse(reward.endsAt()).toInstant())) return false;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int milestoneDay(String id) {
        try { return Integer.parseInt(id.substring(id.lastIndexOf('-') + 1)); }
        catch (Exception ignored) { return Integer.MAX_VALUE; }
    }

    private int nextMilestone(int current) {
        return plugin.getCatalog().streak().keySet().stream().filter(day -> day > current).findFirst().orElse(0);
    }

    private int streakProgress(int current) {
        int next = nextMilestone(current);
        if (next == 0) return 100;
        int previous = plugin.getCatalog().streak().keySet().stream().filter(day -> day <= current).max(Integer::compareTo).orElse(0);
        return Math.max(0, Math.min(100, (current - previous) * 100 / Math.max(1, next - previous)));
    }

    private PlayerStats stats(UUID uuid) { return plugin.getPlaytimeService().cachedStats(uuid); }

    private ZoneId zone() {
        try { return ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); }
        catch (Exception ignored) { return ZoneId.of("Asia/Jakarta"); }
    }

    private boolean isCoolingDown(UUID uuid, String category) {
        if (!plugin.isPeriodicRewardEnabled(category)) return false;
        return nextAvailableAt(uuid, category) > System.currentTimeMillis();
    }

    private String claimType(String category) {
        return switch (category) {
            case "weekly" -> "WEEKLY";
            case "monthly" -> "MONTHLY";
            default -> "DAILY";
        };
    }

    private long nextAvailableAt(UUID uuid, String category) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null && online.hasPermission("dailyreward.cooldown.bypass")) return 0L;
        if (!plugin.isPeriodicRewardEnabled(category)) return 0L;
        String cacheKey = uuid + ":" + category;
        if (cooldownCache.size() > 8192) cooldownCache.clear();
        CompletableFuture<Long> future = cooldownCache.computeIfAbsent(cacheKey, ignored ->
                plugin.getDatabase().getNextAvailableAt(uuid, claimType(category), plugin.getPeriodicCooldownMillis(category)));
        if (!future.isDone()) return 0L;
        try { return future.getNow(0L); }
        catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException ex) {
            cooldownCache.remove(cacheKey, future);
            return 0L;
        }
    }

    private String cooldownText(UUID uuid, String category) {
        long next = nextAvailableAt(uuid, category);
        if (next <= System.currentTimeMillis()) return "Available";
        long totalSeconds = Math.max(0L, (next - System.currentTimeMillis() + 999L) / 1000L);
        long days = totalSeconds / 86_400L;
        long totalHours = totalSeconds / 3_600L;
        long hours = totalHours % 24L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        String pattern = plugin.getConfig().getString("periodic-rewards." + category + ".cooldown-format", "%days%d %hours%h %minutes%m %seconds%s");
        return pattern.replace("%days%", String.valueOf(days))
                .replace("%hours%", String.valueOf(totalHours))
                .replace("%hours24%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%seconds%", String.valueOf(seconds));
    }

    private String cooldownDate(UUID uuid, String category) {
        long next = nextAvailableAt(uuid, category);
        if (next <= System.currentTimeMillis()) return "Available";
        return java.time.format.DateTimeFormatter.ofPattern(plugin.getConfig().getString("time.date-format", "dd MMM yyyy HH:mm"))
                .withZone(zone()).format(Instant.ofEpochMilli(next));
    }

    private String formatMinutes(long minutes) {
        long hours = Math.max(0, minutes) / 60;
        long mins = Math.max(0, minutes) % 60;
        return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
    }

    public void invalidate(UUID uuid) {
        stateCache.keySet().removeIf(key -> key.startsWith(uuid + ":"));
        cooldownCache.keySet().removeIf(key -> key.startsWith(uuid + ":"));
    }
    public void invalidateAll() { stateCache.clear(); cooldownCache.clear(); }
}
