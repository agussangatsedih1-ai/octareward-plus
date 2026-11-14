package id.tempestaria.dailyreward.service;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ActionService {
    private final DailyRewardPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public ActionService(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public boolean execute(Player player, RewardDefinition reward, Map<String, String> extra) {
        boolean success = true;
        for (String raw : reward.actions()) {
            try { executeOne(player, raw, reward.id(), extra); }
            catch (Exception ex) {
                success = false;
                plugin.getLogger().warning("Action failed for reward '" + reward.id() + "': " + ex.getMessage());
            }
        }
        return success;
    }

    public boolean executeRaw(Player player, Iterable<String> actions, String rewardId) {
        boolean success = true;
        for (String raw : actions) {
            try { executeOne(player, raw, rewardId, Map.of()); }
            catch (Exception ex) { success = false; plugin.getLogger().warning("Manual action failed: " + ex.getMessage()); }
        }
        return success;
    }

    private void executeOne(Player player, String line, String rewardId, Map<String, String> extra) {
        if (line == null || line.isBlank()) return;
        String action;
        String payload;
        String trimmed = line.trim();
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int end = trimmed.indexOf(']');
            action = trimmed.substring(1, end).toLowerCase(Locale.ROOT);
            payload = trimmed.substring(end + 1).trim();
        } else {
            action = "console";
            payload = trimmed;
        }
        payload = placeholders(player, payload, rewardId, extra);
        switch (action) {
            case "console" -> { if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload)) throw new IllegalStateException("Console command was not handled: " + payload); }
            case "player" -> {
                if (!plugin.getConfig().getBoolean("actions.allow-player-commands", false))
                    throw new IllegalStateException("Player commands are disabled in config.yml");
                if (player == null || !player.isOnline()) throw new IllegalStateException("Player is offline");
                if (!player.performCommand(payload)) throw new IllegalStateException("Player command was not handled: " + payload);
            }
            case "message" -> { if (player != null && player.isOnline()) player.sendMessage(component(payload)); }
            case "broadcast" -> Bukkit.broadcast(component(payload));
            case "actionbar" -> { if (player != null && player.isOnline()) player.sendActionBar(component(payload)); }
            case "title" -> {
                if (player == null || !player.isOnline()) break;
                String[] parts = payload.split("\\|", 2);
                Component title = component(parts[0]);
                Component subtitle = parts.length > 1 ? component(parts[1]) : Component.empty();
                player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
            }
            case "sound" -> {
                if (player == null || !player.isOnline()) break;
                String[] parts = payload.split("\\s+");
                Sound sound = Sound.valueOf(parts[0].toUpperCase(Locale.ROOT));
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
            case "money" -> {
                if (player == null || !player.isOnline()) throw new IllegalStateException("Player must be online for a Vault deposit");
                double amount = Double.parseDouble(payload);
                if (!Double.isFinite(amount) || amount <= 0.0) throw new IllegalArgumentException("Money amount must be a positive finite number");
                var economy = plugin.getEconomyService();
                if (economy == null || !economy.isAvailable()) throw new IllegalStateException("Vault Economy is unavailable; install Vault and an economy provider");
                if (!economy.deposit(player, amount)) throw new IllegalStateException("Vault could not deposit " + amount + " for " + player.getName());
            }
            case "item" -> {
                if (player == null || !player.isOnline()) break;
                String[] parts = payload.split("\\s+");
                Material material = Material.matchMaterial(parts[0]);
                if (material == null || material.isAir()) throw new IllegalArgumentException("Invalid item material: " + parts[0]);
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material, Math.max(1, Math.min(64, amount))));
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            default -> throw new IllegalArgumentException("Unknown action type: " + action);
        }
    }

    private String placeholders(Player player, String text, String rewardId, Map<String, String> extra) {
        String result = text.replace("%player%", player == null ? "" : player.getName())
                .replace("%uuid%", player == null ? "" : player.getUniqueId().toString())
                .replace("%reward_id%", rewardId)
                .replace("%streak%", extra.getOrDefault("streak", "0"))
                .replace("%playtime%", extra.getOrDefault("playtime", "0"))
                .replace("%event_id%", extra.getOrDefault("event_id", ""));
        var economy = plugin.getEconomyService();
        if (player != null && economy != null && economy.isAvailable()) {
            double balance = economy.getBalance(player);
            result = result.replace("%balance%", Double.toString(balance))
                    .replace("%balance_formatted%", economy.format(balance));
        }
        if (player != null && plugin.getConfig().getBoolean("actions.use-placeholderapi", true)
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                result = (String) papi.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, result);
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().fine("PlaceholderAPI expansion not available: " + ex.getMessage());
            }
        }
        return result;
    }

    public static Component component(String text) {
        if (text.contains("&")) return LEGACY.deserialize(text);
        return MINI.deserialize(text);
    }
}
