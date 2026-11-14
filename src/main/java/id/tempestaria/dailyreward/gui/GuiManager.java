package id.tempestaria.dailyreward.gui;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.DateUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class GuiManager {
    private final DailyRewardPlugin plugin;
    private final NamespacedKey actionKey;
    private YamlConfiguration gui;

    public GuiManager(DailyRewardPlugin plugin) {
        this.plugin = plugin; this.actionKey = new NamespacedKey(plugin, "gui_action"); load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        gui = YamlConfiguration.loadConfiguration(file);
    }

    public void openMain(Player player) {
        int size = plugin.getConfig().getInt("gui.main-size", 27);
        DailyRewardHolder holder = new DailyRewardHolder("main");
        Inventory inv = Bukkit.createInventory(holder, validSize(size), color(plugin.getConfig().getString("gui.main-title", "&8Daily Reward")));
        holder.inventory(inv); fill(inv);
        Map<String, String> categories = Map.of("daily", "Daily Reward", "streak", "Daily Streak", "playtime", "Playtime Reward", "events", "Event Reward", "onetime", "One Time Reward");
        for (String category : categories.keySet()) {
            String path = "main." + category;
            Material material = material(gui.getString(path + ".material", "CHEST"));
            String name = gui.getString(path + ".name", "&f" + categories.get(category));
            List<String> lore = getLoreList(path);
            int slot = plugin.getConfig().getInt("gui.main-slots." + category, 10);
            inv.setItem(slot, createItem(material, name, lore, "open:" + category));
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String category) { openCategory(player, category, 0); }

    public void openCategory(Player player, String category, int requestedPage) {
        category = category.toLowerCase(Locale.ROOT);
        if (category.equals("streak")) { openStreak(player); return; }
        final String targetCategory = category;
        final int requestedPageIndex = Math.max(0, requestedPage);
        List<RewardDefinition> rewards = switch (category) {
            case "daily" -> new ArrayList<>(plugin.getCatalog().daily().values());
            case "playtime" -> new ArrayList<>(plugin.getCatalog().playtime().values());
            case "events" -> new ArrayList<>(plugin.getCatalog().events().values());
            case "onetime" -> new ArrayList<>(plugin.getCatalog().oneTime().values());
            default -> List.of();
        };
        if (rewards.isEmpty()) { player.sendMessage(DailyRewardPlugin.color("&cNo rewards are configured for this category.")); return; }

        CompletableFuture<Long> playtimeFuture = CompletableFuture.completedFuture(plugin.getPlaytimeService().minutesForOnline(player) * 60_000L);
        CompletableFuture<Map<String, Boolean>> statesFuture = playtimeFuture.thenCompose(millis -> {
            long minutes = millis / 60_000L;
            Map<String, CompletableFuture<Boolean>> checks = new LinkedHashMap<>();
            if (targetCategory.equals("daily")) {
                for (RewardDefinition r : rewards) checks.put(r.id(), plugin.getDatabase().hasClaim(player.getUniqueId(), r.id(), "DAILY", currentPeriod()));
            } else if (targetCategory.equals("events")) {
                for (RewardDefinition r : rewards) {
                    CompletableFuture<Boolean> active = plugin.getDatabase().hasActiveEvent(r.id(), player.getUniqueId());
                    CompletableFuture<Boolean> used = plugin.getDatabase().hasReachedClaimLimit(player.getUniqueId(), r.id(), "EVENT", r.claimLimit());
                    checks.put(r.id(), active);
                    checks.put(r.id() + "_claimed", used);
                }
            } else if (targetCategory.equals("onetime")) {
                for (RewardDefinition r : rewards) checks.put(r.id(), plugin.getDatabase().hasClaim(player.getUniqueId(), r.id(), "ONETIME", "once"));
            } else if (targetCategory.equals("playtime")) {
                for (RewardDefinition r : rewards) {
                    String period = "once";
                    if (r.repeat()) {
                        long interval = r.repeatIntervalMinutes() > 0 ? r.repeatIntervalMinutes() : Math.max(1, r.requiredMinutes());
                        period = "interval-" + (minutes / interval);
                    }
                    checks.put(r.id(), plugin.getDatabase().hasClaim(player.getUniqueId(), r.id(), "PLAYTIME", period));
                }
            }
            CompletableFuture<?>[] all = checks.values().toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(all).thenApply(v -> {
                Map<String, Boolean> result = new HashMap<>();
                checks.forEach((id, future) -> result.put(id, future.getNow(false)));
                return result;
            });
        });

        playtimeFuture.thenCombine(statesFuture, (millis, states) -> new Object[]{millis, states})
                .whenComplete((loaded, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) plugin.getLogger().warning("Could not load GUI reward status: " + error.getMessage());
                    long minutes = error == null ? (long) loaded[0] / 60_000L : 0;
                    @SuppressWarnings("unchecked") Map<String, Boolean> states = error == null ? (Map<String, Boolean>) loaded[1] : Map.of();
                    buildRewardPage(player, targetCategory, rewards, minutes, states, requestedPageIndex);
                }));
    }

    private void buildRewardPage(Player player, String category, List<RewardDefinition> rewards, long minutes,
                                 Map<String, Boolean> states, int requestedPage) {
        int size = Math.max(18, validSize(plugin.getConfig().getInt("gui.size", 54)));
        int contentSlots = size - 9;
        int pageCount = Math.max(1, (rewards.size() + contentSlots - 1) / contentSlots);
        int pageIndex = Math.min(requestedPage, pageCount - 1);
        int startIndex = pageIndex * contentSlots;
        int endIndex = Math.min(rewards.size(), startIndex + contentSlots);
        DailyRewardHolder holder = new DailyRewardHolder(category, pageIndex);
        String title = plugin.getConfig().getString("gui.category-title", "&8%category%").replace("%category%", categoryName(category));
        if (pageCount > 1) title += " &8(" + (pageIndex + 1) + "/" + pageCount + ")";
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory(inv); fill(inv);
        Set<Integer> occupiedSlots = new HashSet<>();
        for (int rewardIndex = startIndex; rewardIndex < endIndex; rewardIndex++) {
            RewardDefinition reward = rewards.get(rewardIndex);
            boolean claimed = category.equals("events")
                    ? states.getOrDefault(reward.id() + "_claimed", false)
                    : states.getOrDefault(reward.id(), false);
            boolean activeEvent = category.equals("events") && states.getOrDefault(reward.id(), false);
            boolean eligible = reward.enabled() && (reward.permission().isBlank() || player.hasPermission(reward.permission()));
            if (category.equals("playtime")) {
                eligible &= player.hasPermission("dailyreward.playtime.bypass") || minutes >= reward.requiredMinutes();
            }
            if (category.equals("events")) eligible &= activeEvent && !claimed;
            if (category.equals("onetime") && !reward.startsAt().isBlank()) eligible &= !Instant.now().isBefore(parseInstant(reward.startsAt(), Instant.EPOCH));
            if (category.equals("onetime") && !reward.endsAt().isBlank()) eligible &= !Instant.now().isAfter(parseInstant(reward.endsAt(), Instant.MAX));
            String status = claimed ? "&7CLAIMED" : eligible ? "&aAVAILABLE" : "&cLOCKED / REQUIREMENTS NOT MET";
            List<String> lore = new ArrayList<>(reward.lore());
            lore.add(" "); lore.add("&7Status: " + status);
            if (category.equals("playtime")) {
                long required = Math.max(1, reward.requiredMinutes());
                int percent = (int) Math.min(100, minutes * 100 / required);
                lore.add("&7Playtime: &f" + minutes + " / " + reward.requiredMinutes() + " minutes");
                lore.add("&8[" + progressBar(percent, 12) + "&8] &f" + percent + "%");
            }
            if (category.equals("events") && !activeEvent) lore.add("&eWait for an administrator to activate this event.");
            String action = switch (category) {
                case "daily" -> "claim:daily:" + reward.id();
                case "playtime" -> "claim:playtime:" + reward.id();
                case "events" -> "claim:events:" + reward.id();
                case "onetime" -> "claim:onetime:" + reward.id();
                default -> "";
            };
            int preferredSlot = reward.slot();
            int slot = preferredSlot >= 0 && preferredSlot < contentSlots && !occupiedSlots.contains(preferredSlot)
                    ? preferredSlot : firstFreeSlot(occupiedSlots, contentSlots);
            if (slot >= 0) {
                occupiedSlots.add(slot);
                inv.setItem(slot, createItem(material(reward.material()), reward.displayName(), lore, claimed ? "" : action));
            }
        }
        addBack(inv);
        if (pageCount > 1) addPagination(inv, category, pageIndex, pageCount, contentSlots);
        player.openInventory(inv);
    }

    private void openStreak(Player player) {
        plugin.getDatabase().getStats(player.getUniqueId()).whenComplete((stats, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerStats data = error == null ? stats : PlayerStats.empty();
            DailyRewardHolder holder = new DailyRewardHolder("streak");
            Inventory inv = Bukkit.createInventory(holder, validSize(plugin.getConfig().getInt("gui.size", 54)), color("&8Daily Streak"));
            holder.inventory(inv); fill(inv);
            ItemStack info = createItem(Material.FIREWORK_STAR, "&dCurrent Streak: &f" + data.currentStreak(),
                    List.of("&7Best streak: &f" + data.bestStreak(), "&7Missed-day policy: &f" + plugin.getCatalog().missedDayPolicy()), "");
            inv.setItem(4, info);
            for (Map.Entry<Integer, RewardDefinition> entry : plugin.getCatalog().streak().entrySet()) {
                int day = entry.getKey(); RewardDefinition r = entry.getValue();
                String state = data.currentStreak() >= day ? "&aReached" : "&cNot reached";
                List<String> lore = new ArrayList<>(r.lore()); lore.add(" "); lore.add("&7Milestone: &fDay " + day); lore.add("&7Status: " + state);
                int slot = Math.max(0, Math.min(inv.getSize() - 1, r.slot()));
                inv.setItem(slot, createItem(material(r.material()), r.displayName(), lore, ""));
            }
            addBack(inv); player.openInventory(inv);
        }));
    }

    public void handleClick(Player player, Inventory inv, int rawSlot) {
        if (!(inv.getHolder() instanceof DailyRewardHolder holder)) return;
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        ItemStack item = inv.getItem(rawSlot); if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;
        if (action.startsWith("open:")) openCategory(player, action.substring(5));
        else if (action.startsWith("page:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
                try { openCategory(player, parts[1], Integer.parseInt(parts[2])); }
                catch (NumberFormatException ignored) { }
            }
        }
        else if (action.startsWith("claim:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) plugin.getClaimService().claim(player, parts[1], parts[2]);
        } else if (action.equals("back")) openMain(player);
    }

    private void addBack(Inventory inv) {
        int preferredSlot = plugin.getConfig().getInt("gui.back-slot", 49);
        int slot = preferredSlot >= 0 && preferredSlot < inv.getSize() ? preferredSlot : inv.getSize() - 5;
        ConfigurationSectionCompat back = new ConfigurationSectionCompat(gui.getString("buttons.back.material", "ARROW"), gui.getString("buttons.back.name", "&cBack"), getLoreList("buttons.back"));
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, createItem(material(back.material), back.name, back.lore, "back"));
    }

    private void addPagination(Inventory inv, String category, int pageIndex, int pageCount, int contentSlots) {
        int previousSlot = plugin.getConfig().getInt("gui.previous-slot", inv.getSize() - 9);
        int nextSlot = plugin.getConfig().getInt("gui.next-slot", inv.getSize() - 1);
        if (previousSlot < contentSlots || previousSlot >= inv.getSize()) previousSlot = inv.getSize() - 9;
        if (nextSlot < contentSlots || nextSlot >= inv.getSize() || nextSlot == previousSlot) nextSlot = inv.getSize() - 1;
        if (pageIndex > 0) {
            inv.setItem(previousSlot, createItem(material(gui.getString("buttons.previous.material", "ARROW")),
                    gui.getString("buttons.previous.name", "&ePrevious Page"),
                    getLoreList("buttons.previous"), "page:" + category + ":" + (pageIndex - 1)));
        }
        if (pageIndex + 1 < pageCount) {
            inv.setItem(nextSlot, createItem(material(gui.getString("buttons.next.material", "ARROW")),
                    gui.getString("buttons.next.name", "&eNext Page"),
                    getLoreList("buttons.next"), "page:" + category + ":" + (pageIndex + 1)));
        }
    }

    private List<String> getLoreList(String path) {
        List<String> lore = gui.getStringList(path + ".Lore");
        if (lore.isEmpty()) lore = gui.getStringList(path + ".lore");
        return lore;
    }

    private int firstFreeSlot(Set<Integer> occupied, int limit) {
        for (int slot = 0; slot < limit; slot++) if (!occupied.contains(slot)) return slot;
        return -1;
    }

    private record ConfigurationSectionCompat(String material, String name, List<String> lore) {}

    private void fill(Inventory inv) {
        if (!plugin.getConfig().getBoolean("gui.filler.enabled", true)) return;
        ItemStack filler = createItem(material(plugin.getConfig().getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE")),
                plugin.getConfig().getString("gui.filler.name", " "), List.of(), "");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material == null ? Material.CHEST : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(id.tempestaria.dailyreward.service.ActionService.component(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(id.tempestaria.dailyreward.service.ActionService::component).toList());
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta); return item;
    }

    private static Material material(String name) { Material m = Material.matchMaterial(name == null ? "CHEST" : name); return m == null ? Material.CHEST : m; }
    private static String color(String value) { return value.replace('&', '§'); }
    private static int validSize(int size) { return Math.max(9, Math.min(54, (int) Math.ceil(size / 9.0) * 9)); }
    private String progressBar(int percent, int length) {
        int filled = Math.max(0, Math.min(length, percent * length / 100));
        return "&a" + "|".repeat(filled) + "&7" + "|".repeat(length - filled);
    }
    private String categoryName(String category) { return switch (category) { case "daily" -> "Daily Reward"; case "playtime" -> "Playtime Reward"; case "events" -> "Event Reward"; case "onetime" -> "One Time Reward"; default -> category; }; }
    private String currentPeriod() {
        ZoneId zone; try { zone = ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); } catch (Exception e) { zone = ZoneId.of("Asia/Jakarta"); }
        LocalTime reset; try { reset = LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00")); } catch (Exception e) { reset = LocalTime.MIDNIGHT; }
        return DateUtil.periodDate(Instant.now(), zone, reset).toString();
    }
    private Instant parseInstant(String text, Instant fallback) { try { return OffsetDateTime.parse(text).toInstant(); } catch (Exception e) { return fallback; } }
}
