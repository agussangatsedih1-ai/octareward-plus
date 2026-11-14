package id.tempestaria.dailyreward.command;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.api.DailyRewardEventTriggeredEvent;
import id.tempestaria.dailyreward.service.ActionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.time.OffsetDateTime;
import java.util.*;

public final class DailyRewardCommand implements CommandExecutor, TabCompleter {
    private final DailyRewardPlugin plugin;
    public DailyRewardCommand(DailyRewardPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("This command requires a player for the GUI."); return true; }
            if (!sender.hasPermission("dailyreward.use")) { sender.sendMessage(msg("no-permission")); return true; }
            plugin.getGuiManager().openMain(player); return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && Set.of("daily", "weekly", "monthly", "streak", "playtime", "events", "onetime", "supporter").contains(sub)) {
            if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return true; }
            if (!sender.hasPermission("dailyreward.use")) { sender.sendMessage(msg("no-permission")); return true; }
            plugin.getGuiManager().openCategory(player, sub); return true;
        }
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("dailyreward.reload")) { sender.sendMessage(msg("no-permission")); return true; }
                plugin.reloadPluginConfig(); sender.sendMessage(msg("reloaded")); return true;
            }
            case "stats" -> {
                OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage("Usage: /dailyreward stats <player>"); return true; }
                plugin.getDatabase().getStats(target.getUniqueId()).whenComplete((stats, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) sender.sendMessage(msg("claim-error"));
                    else sender.sendMessage(ActionService.component("&bOctaReward Plus stats for &f" + target.getName() + "&b: playtime &f" + stats.playtimeMillis() / 60_000 + "m&b, streak &f" + stats.currentStreak() + "&b, best &f" + stats.bestStreak()));
                }));
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("dailyreward.give")) { sender.sendMessage(msg("no-permission")); return true; }
                if (args.length < 3) { sender.sendMessage("Usage: /dailyreward give <player> <reward-id>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage(msg("unknown-player")); return true; }
                plugin.getClaimService().grantByAdmin(sender instanceof Player p ? p : null, target, args[2]); return true;
            }
            case "reset" -> {
                if (!sender.hasPermission("dailyreward.reset")) { sender.sendMessage(msg("no-permission")); return true; }
                if (args.length < 3) { sender.sendMessage("Usage: /dailyreward reset <player> <reward-id>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                plugin.getClaimService().reset(sender instanceof Player p ? p : null, target.getUniqueId(), args[2]);
                sender.sendMessage(ActionService.component("&aReset claim &f" + args[2] + " &afor &f" + args[1] + "&a.")); return true;
            }
            case "streak" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                    if (!sender.hasPermission("dailyreward.reset")) { sender.sendMessage(msg("no-permission")); return true; }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                    plugin.getDatabase().resetStreak(target.getUniqueId()).thenCompose(ignored -> plugin.getDatabase().getStats(target.getUniqueId()))
                            .thenAccept(stats -> {
                                plugin.getPlaytimeService().updateStreakCache(target.getUniqueId(), stats.currentStreak(), stats.bestStreak());
                                plugin.invalidatePlaceholderState(target.getUniqueId());
                                plugin.getDatabase().audit(sender instanceof Player p ? p.getUniqueId() : null, target.getUniqueId(), "STREAK_RESET", "manual");
                            });
                    sender.sendMessage(ActionService.component("&aStreak reset for &f" + args[2] + "&a.")); return true;
                }
                sender.sendMessage("Usage: /dailyreward streak reset <player>"); return true;
            }
            case "playtime" -> {
                if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
                    if (!sender.hasPermission("dailyreward.admin")) { sender.sendMessage(msg("no-permission")); return true; }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) { sender.sendMessage("The player must be online to update the EssentialsX playtime statistic."); return true; }
                    long minutes; try { minutes = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage("Minutes must be a number."); return true; }
                    if (minutes < 0) { sender.sendMessage("Minutes must be zero or greater."); return true; }
                    long ticks = Math.min(Integer.MAX_VALUE, minutes * 1_200L);
                    target.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, (int) ticks);
                    plugin.getPlaytimeService().refresh(target);
                    plugin.invalidatePlaceholderState(target.getUniqueId());
                    plugin.getDatabase().audit(sender instanceof Player p ? p.getUniqueId() : null, target.getUniqueId(), "PLAYTIME_SET", String.valueOf(minutes));
                    sender.sendMessage(ActionService.component("&aSet EssentialsX playtime for &f" + args[2] + " &ato &f" + minutes + " minutes&a.")); return true;
                }
                sender.sendMessage("Usage: /dailyreward playtime set <player> <minutes>"); return true;
            }
            case "event" -> { return eventCommand(sender, args); }
            case "debug" -> {
                if (!sender.hasPermission("dailyreward.admin")) { sender.sendMessage(msg("no-permission")); return true; }
                sender.sendMessage(ActionService.component("&bOctaReward Plus debug: &fstorage=" + plugin.getConfig().getString("storage.type") + ", &fEssentialsX=" + Bukkit.getPluginManager().isPluginEnabled("Essentials") + ", &fPAPI=" + Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") + ", &fVault=" + Bukkit.getPluginManager().isPluginEnabled("Vault")));
                return true;
            }
            default -> { sender.sendMessage(msg("usage")); return true; }
        }
    }

    private boolean eventCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dailyreward.event.manage")) { sender.sendMessage(msg("no-permission")); return true; }
        if (args.length < 3) { sender.sendMessage("Usage: /dailyreward event trigger <player> <event-id> | triggerall <event-id> | end <event-id>"); return true; }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("end")) {
            var reward = plugin.getCatalog().findEvent(args[2]);
            if (reward.isEmpty()) { sender.sendMessage(msg("unknown-reward")); return true; }
            String id = reward.get().id();
            plugin.getDatabase().endEvent(id).thenRun(() -> {
                plugin.invalidateAllPlaceholderStates();
                plugin.getDatabase().audit(sender instanceof Player p ? p.getUniqueId() : null, null, "EVENT_END", id);
            });
            sender.sendMessage(ActionService.component("&cEnded event &f" + id + "&c.")); return true;
        }
        if (action.equals("trigger") && args.length >= 4) {
            Player target = Bukkit.getPlayerExact(args[2]); if (target == null) { sender.sendMessage(msg("unknown-player")); return true; }
            String id = args[3]; triggerEvent(sender, id, target); return true;
        }
        if (action.equals("triggerall")) { triggerEvent(sender, args[2], null); return true; }
        sender.sendMessage("Usage: /dailyreward event trigger <player> <event-id> | triggerall <event-id> | end <event-id>"); return true;
    }

    private void triggerEvent(CommandSender sender, String eventId, Player target) {
        var rewardOptional = plugin.getCatalog().findEvent(eventId);
        if (rewardOptional.isEmpty()) { sender.sendMessage(msg("unknown-reward")); return; }
        var reward = rewardOptional.get();
        String canonicalEventId = reward.id();
        long now = System.currentTimeMillis();
        long start = parseDate(reward.startsAt(), now);
        long end = parseDate(reward.endsAt(), now + 86_400_000L);
        plugin.getDatabase().triggerEvent(canonicalEventId, target == null ? null : target.getUniqueId(), start, end)
                .whenComplete((instance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) { sender.sendMessage(ActionService.component("&cCould not trigger event: " + error.getMessage())); return; }
                    Bukkit.getPluginManager().callEvent(new DailyRewardEventTriggeredEvent(canonicalEventId, instance, target));
                    if (target == null) plugin.invalidateAllPlaceholderStates();
                    else plugin.invalidatePlaceholderState(target.getUniqueId());
                    plugin.getDatabase().audit(sender instanceof Player p ? p.getUniqueId() : null,
                            target == null ? null : target.getUniqueId(), "EVENT_TRIGGER", canonicalEventId + ":" + instance);
                    sender.sendMessage(ActionService.component("&aTriggered event &f" + canonicalEventId + (target == null ? " &afor all players." : " &afor &f" + target.getName() + "&a.")));
                }));
    }

    private long parseDate(String s, long fallback) { try { return OffsetDateTime.parse(s).toInstant().toEpochMilli(); } catch (Exception e) { return fallback; } }
    private String msg(String key) { return DailyRewardPlugin.color(plugin.getMessages().getString(key, "&c" + key)); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("daily", "weekly", "monthly", "streak", "playtime", "events", "onetime", "supporter", "stats", "reload", "give", "reset", "event", "debug"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("event")) return filter(List.of("trigger", "triggerall", "end"), args[1]);
        List<String> onlineNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2 && Set.of("give", "reset", "stats").contains(args[0].toLowerCase(Locale.ROOT))) return filter(onlineNames, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("streak")) return filter(List.of("reset"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("playtime")) return filter(List.of("set"), args[1]);
        if (args.length == 3 && Set.of("give", "reset").contains(args[0].toLowerCase(Locale.ROOT)))
            return filter(plugin.getCatalog().all().stream().map(r -> r.id()).toList(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("streak") && args[1].equalsIgnoreCase("reset")) return filter(onlineNames, args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("playtime") && args[1].equalsIgnoreCase("set")) return filter(onlineNames, args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && Set.of("triggerall", "end").contains(args[1].toLowerCase(Locale.ROOT)))
            return filter(eventNames(), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("trigger"))
            return filter(eventNames(), args[3]);
        return Collections.emptyList();
    }

    private List<String> eventNames() {
        List<String> names = new ArrayList<>();
        for (var reward : plugin.getCatalog().events().values()) {
            names.add(reward.id());
            if (!reward.eventId().isBlank() && !names.contains(reward.eventId())) names.add(reward.eventId());
        }
        return names;
    }

    private List<String> filter(List<String> values, String prefix) { return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList(); }
}
