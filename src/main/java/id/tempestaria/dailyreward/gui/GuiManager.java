package id.tempestaria.dailyreward.gui;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.config.RewardDefinition;
import id.tempestaria.dailyreward.db.PlayerStats;
import id.tempestaria.dailyreward.util.RewardPeriodUtil;
import id.tempestaria.dailyreward.util.DateUtil;
import id.tempestaria.dailyreward.service.ActionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.File;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Builds the reward menus. All database lookups are asynchronous; Bukkit inventory work stays on the main thread. */
public final class GuiManager {
    private static final int SNAKE_TRACK_SLOTS = 23;
    private final DailyRewardPlugin plugin;
    private final NamespacedKey actionKey;
    private YamlConfiguration gui;

    public GuiManager(DailyRewardPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenCountdowns, 20L, 20L);
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        gui = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource("gui.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                gui.setDefaults(defaults);
                gui.options().copyDefaults(true);
                gui.save(file);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not merge GUI defaults: " + ex.getMessage());
        }
    }


    private void refreshOpenCountdowns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            if (!(inv.getHolder() instanceof DailyRewardHolder holder)) continue;
            String category = holder.page();
            Instant reset = switch (category) {
                case "daily-periods", "daily" -> nextDailyReset();
                case "weekly" -> nextWeeklyReset();
                case "monthly" -> nextMonthlyReset();
                default -> null;
            };
            if (reset == null) continue;
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || !item.hasItemMeta() || item.getItemMeta().lore() == null) continue;
                ItemMeta meta = item.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
                boolean changed = false;
                for (int i = 0; i < lore.size(); i++) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
                    if (plain.contains("Next reset:")) {
                        lore.set(i, ActionService.component("&8⌛ &7Next reset: &f" + countdown(reset)));
                        changed = true;
                    }
                }
                if (changed) { meta.lore(lore); item.setItemMeta(meta); }
            }
        }
    }

    public void openMain(Player player) {
        openHub(player, "main", "&8✦ &6OctaReward Plus", List.of(
                new MenuEntry("basic", Material.CHEST, "&6✦ &lBasic Reward", List.of("&7Your regular reward collection.", "", "&e➤ Open Basic Rewards"), 20),
                new MenuEntry("streak", Material.FIREWORK_STAR, "&d✧ &lDaily Streak", List.of("&7Earn a reward for every consecutive day.", "&d✦ One milestone per day.", "", "&d➤ Follow your streak"), 22),
                new MenuEntry("playtime", Material.CLOCK, "&b✦ &lPlaytime Reward", List.of("&7A 100-hour progression journey.", "&b⌚ New milestones every hour.", "", "&b➤ Follow your playtime"), 24),
                new MenuEntry("extra", Material.NETHER_STAR, "&5✦ &lExtra Reward", List.of("&7Limited-time celebrations and special gifts.", "&5✧ Seasonal reward collections.", "", "&5➤ Explore Extra Rewards"), 31)
        ));
    }

    private record MenuEntry(String action, Material material, String name, List<String> lore, int slot) {}

    private void openHub(Player player, String id, String title, List<MenuEntry> entries) {
        DailyRewardHolder holder = new DailyRewardHolder(id);
        Inventory inv = Bukkit.createInventory(holder, 54, color(title));
        holder.inventory(inv);
        fill(inv);
        for (MenuEntry entry : entries) {
            List<String> lore = new ArrayList<>(entry.lore());
            if (entry.action().equals("daily-periods")) {
                lore.add(""); lore.add("&8⌛ Daily: &f" + countdown(nextDailyReset()));
                lore.add("&8⌛ Weekly: &f" + countdown(nextWeeklyReset()));
                lore.add("&8⌛ Monthly: &f" + countdown(nextMonthlyReset()));
            }
            inv.setItem(entry.slot(), createItem(entry.material(), entry.name(), lore, "menu:" + entry.action(), true));
        }
        inv.setItem(49, createItem(Material.ARROW, "&c❮ Back", List.of("&7Return to the previous menu."), "back", false));
        player.openInventory(inv);
    }

    private Instant nextDailyReset() {
        try { return DateUtil.nextReset(Instant.now(), zone(), LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00"))); }
        catch (Exception ex) { return Instant.now().plusSeconds(86400); }
    }
    private Instant nextWeeklyReset() {
        ZonedDateTime now = Instant.now().atZone(zone());
        return now.with(java.time.DayOfWeek.MONDAY).toLocalDate().plusWeeks(1).atStartOfDay(zone()).toInstant();
    }
    private Instant nextMonthlyReset() {
        ZonedDateTime now = Instant.now().atZone(zone());
        return now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone()).toInstant();
    }
    private String countdown(Instant target) {
        long seconds = Math.max(0, Duration.between(Instant.now(), target).getSeconds());
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return (days > 0 ? days + "d " : "") + hours + "h " + minutes + "m " + seconds + "s";
    }

    private void openBasic(Player player) {
        openHub(player, "basic", "&8✦ &6Basic Rewards", List.of(
                new MenuEntry("daily-reward", Material.CHEST, "&e✦ &lDaily Reward", List.of("&7Daily, weekly and monthly gifts.", "&8⌛ &7Resets: &f" + countdown(nextDailyReset()), "", "&e➤ Open"), 21),
                new MenuEntry("supporter", Material.DIAMOND, "&b✧ &lSupporter Reward", List.of("&7Exclusive rewards for your server ranks.", "&b✦ &7Permission-based with LuckPerms support.", "", "&b➤ View rank rewards"), 23)
        ));
    }
    private void openDailyPeriods(Player player) {
        openHub(player, "daily-periods", "&8✦ &eDaily Reward", List.of(
                new MenuEntry("daily", Material.SUNFLOWER, "&e☀ &lDaily", List.of("&7Next reset: &f" + countdown(nextDailyReset())), 20),
                new MenuEntry("weekly", Material.ENDER_CHEST, "&b✦ &lWeekly", List.of("&7Next reset: &f" + countdown(nextWeeklyReset())), 22),
                new MenuEntry("monthly", Material.NETHER_STAR, "&6✧ &lMonthly", List.of("&7Next reset: &f" + countdown(nextMonthlyReset())), 24)
        ));
    }
    private void openExtra(Player player) {
        openHub(player, "extra", "&8✦ &5Extra Rewards", List.of(
                new MenuEntry("seasonal", Material.FIREWORK_STAR, "&d✦ &lSeasonal Reward", List.of("&7Christmas, Winter, Spring, Easter and more.", "&d✧ &7Limited-time reward collections."), 22)
        ));
    }
    private void openSeasonal(Player player) {
        openHub(player, "seasonal", "&8✦ &5Seasonal Collections", List.of(
                new MenuEntry("season-winter", Material.PACKED_ICE, "&b❄ &lWinter Festival", List.of("&7Explore winter-themed rewards."), 19),
                new MenuEntry("season-christmas", Material.SPRUCE_LEAVES, "&c✦ &lChristmas Celebration", List.of("&7Explore Christmas rewards."), 21),
                new MenuEntry("season-spring", Material.CHERRY_SAPLING, "&d✿ &lSpring Awakening", List.of("&7Explore spring-themed rewards."), 23),
                new MenuEntry("season-easter", Material.EGG, "&e✧ &lEaster Festival", List.of("&7Explore Easter rewards."), 25)
        ));
    }
    private void openSeasonCategory(Player player, String season) {
        openCategoryFiltered(player, "events", 0, season);
    }
    private void openSupporter(Player player) { openCategory(player, "supporter"); }

    public void openCategory(Player player, String category) { openCategory(player, category, 0); }

    public void openCategory(Player player, String category, int requestedPage) {
        openCategoryFiltered(player, category, requestedPage, null);
    }

    private void openCategoryFiltered(Player player, String category, int requestedPage, String seasonFilter) {
        category = category.toLowerCase(Locale.ROOT);
        if (category.equals("streak") || category.equals("playtime")) {
            openSnakeCategory(player, category, requestedPage);
            return;
        }

        final String target = category;
        List<RewardDefinition> rewards = new ArrayList<>(rewardsFor(category).values());
        if (seasonFilter != null && category.equals("events"))
            rewards.removeIf(reward -> !reward.season().equalsIgnoreCase(seasonFilter));
        if (rewards.isEmpty()) {
            player.sendMessage(DailyRewardPlugin.color("&cNo rewards are configured for this category."));
            return;
        }

        Map<String, CompletableFuture<Boolean>> checks = new LinkedHashMap<>();
        String claimType = claimType(category);
        String period = currentPeriod(category);
        for (RewardDefinition reward : rewards) {
            if (category.equals("events")) {
                checks.put(reward.id(), plugin.getDatabase().hasActiveEvent(reward.id(), player.getUniqueId()));
                checks.put(reward.id() + "_claimed", plugin.getDatabase().hasReachedClaimLimit(player.getUniqueId(), reward.id(), "EVENT", reward.claimLimit()));
            } else {
                checks.put(reward.id(), plugin.getDatabase().hasClaim(player.getUniqueId(), reward.id(), claimType, period));
            }
        }
        CompletableFuture.allOf(checks.values().toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    Map<String, Boolean> states = new HashMap<>();
                    if (error == null) checks.forEach((id, future) -> states.put(id, future.getNow(false)));
                    else plugin.getLogger().warning("Could not load reward menu state: " + error.getMessage());
                    buildRewardPage(player, target, rewards, states, Math.max(0, requestedPage));
                }));
    }

    private void buildRewardPage(Player player, String category, List<RewardDefinition> rewards,
                                 Map<String, Boolean> states, int requestedPage) {
        int size = 54;
        int contentSlots = size - 9;
        int pageCount = Math.max(1, (rewards.size() + contentSlots - 1) / contentSlots);
        int page = Math.min(requestedPage, pageCount - 1);
        int start = page * contentSlots;
        int end = Math.min(rewards.size(), start + contentSlots);
        DailyRewardHolder holder = new DailyRewardHolder(category, page);
        String title = plugin.getConfig().getString("gui.category-title", "&8✦ &b%category%").replace("%category%", categoryName(category));
        if (pageCount > 1) title += " &8[&f" + (page + 1) + "&7/&f" + pageCount + "&8]";
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory(inv);
        fill(inv);

        Set<Integer> occupied = new HashSet<>();
        for (int i = start; i < end; i++) {
            RewardDefinition reward = rewards.get(i);
            boolean claimed = category.equals("events")
                    ? states.getOrDefault(reward.id() + "_claimed", false)
                    : states.getOrDefault(reward.id(), false);
            boolean eventActive = category.equals("events") && states.getOrDefault(reward.id(), false);
            boolean eligible = reward.enabled() && (reward.permission().isBlank() || player.hasPermission(reward.permission()));
            eligible &= withinWindow(reward.startsAt(), reward.endsAt());
            if (category.equals("events")) eligible &= eventActive;
            if (claimed) eligible = false;

            String action = eligible ? "claim:" + category + ":" + reward.id() : "";
            List<String> lore = fancyLore(reward.lore());
            if (category.equals("daily") || category.equals("weekly") || category.equals("monthly")) {
                Instant resetAt = category.equals("daily") ? nextDailyReset() : category.equals("weekly") ? nextWeeklyReset() : nextMonthlyReset();
                lore.add("&8⌛ &7Next reset: &f" + countdown(resetAt));
            }
            if (category.equals("supporter")) lore.add("&b✦ &7Rank access is checked through Bukkit permissions (LuckPerms compatible). ");
            if (category.equals("events")) {
                lore.add("&8✦ &7Season: &d" + (reward.season().isBlank() ? "Special Event" : titleCase(reward.season())));
                lore.add("&8✦ &7Event: &f" + (reward.eventName().isBlank() ? reward.displayName() : reward.eventName()));
                lore.add("&8✦ &7Schedule: &f" + dateLabel(reward.startsAt()) + " &8→ &f" + dateLabel(reward.endsAt()));
                if (!eventActive) lore.add("&c✘ &7Event is not currently active.");
            }
            appendStatusLore(lore, claimed, eligible);
            int preferred = reward.slot();
            int slot = preferred >= 0 && preferred < contentSlots && !occupied.contains(preferred)
                    ? preferred : firstFreeSlot(occupied, contentSlots);
            if (slot < 0) continue;
            occupied.add(slot);
            Material itemMaterial = claimed ? statusMaterial("claimed", Material.LIME_STAINED_GLASS_PANE)
                    : eligible ? material(reward.material()) : statusMaterial("locked", Material.GRAY_STAINED_GLASS_PANE);
            String decoratedName = statusName(reward.displayName(), claimed, eligible);
            inv.setItem(slot, createItem(itemMaterial, decoratedName, lore, action, eligible || claimed));
        }
        addBack(inv);
        if (pageCount > 1) addPagination(inv, category, page, pageCount, contentSlots);
        player.openInventory(inv);
    }

    /** Playtime and streak are presented as a 5 x 9 serpentine progression board. */
    private void openSnakeCategory(Player player, String category, int requestedPage) {
        List<RewardDefinition> rewards = new ArrayList<>(rewardsFor(category).values());
        if (category.equals("playtime")) {
            rewards.sort(Comparator.comparingLong(RewardDefinition::requiredMinutes).thenComparing(RewardDefinition::id));
        }
        if (category.equals("streak")) {
            rewards.sort(Comparator.comparingInt((RewardDefinition reward) -> milestoneDay(reward.id())));
        }
        if (rewards.isEmpty()) {
            player.sendMessage(DailyRewardPlugin.color("&cNo rewards are configured for this category."));
            return;
        }
        CompletableFuture<PlayerStats> statsFuture = plugin.getDatabase().getStats(player.getUniqueId());
        long liveMinutes = category.equals("playtime") ? plugin.getPlaytimeService().minutesForOnline(player) : 0L;
        Map<String, CompletableFuture<Boolean>> checks = new LinkedHashMap<>();
        for (RewardDefinition reward : rewards) {
            if (category.equals("streak")) checks.put(reward.id(), plugin.getDatabase().hasAnyClaim(player.getUniqueId(), reward.id(), "STREAK"));
            else {
                String period = "once";
                if (reward.repeat()) {
                    long interval = reward.repeatIntervalMinutes() > 0 ? reward.repeatIntervalMinutes() : Math.max(1, reward.requiredMinutes());
                    period = "interval-" + (liveMinutes / interval);
                }
                checks.put(reward.id(), plugin.getDatabase().hasClaim(player.getUniqueId(), reward.id(), "PLAYTIME", period));
            }
        }
        CompletableFuture<?>[] all = new CompletableFuture<?>[checks.size() + 1];
        all[0] = statsFuture;
        int index = 1;
        for (CompletableFuture<Boolean> future : checks.values()) all[index++] = future;
        CompletableFuture.allOf(all).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerStats stats = error == null ? statsFuture.getNow(PlayerStats.empty()) : PlayerStats.empty();
            Map<String, Boolean> claimed = new HashMap<>();
            if (error == null) checks.forEach((id, future) -> claimed.put(id, future.getNow(false)));
            else plugin.getLogger().warning("Could not load progression board: " + error.getMessage());
            long minutes = category.equals("playtime") ? liveMinutes : 0L;
            buildSnakePage(player, category, rewards, claimed, stats, minutes, requestedPage);
        }));
    }

    private void buildSnakePage(Player player, String category, List<RewardDefinition> rewards,
                                Map<String, Boolean> claimedStates, PlayerStats stats, long minutes, int requestedPage) {
        int pageCount = Math.max(1, (rewards.size() + SNAKE_TRACK_SLOTS - 1) / SNAKE_TRACK_SLOTS);
        int page = Math.min(Math.max(0, requestedPage), pageCount - 1);
        int start = page * SNAKE_TRACK_SLOTS;
        int end = Math.min(rewards.size(), start + SNAKE_TRACK_SLOTS);
        String title = category.equals("playtime") ? "&8✦ &aPlaytime Trail" : "&8✦ &dDaily Streak Trail";
        if (pageCount > 1) title += " &8[&f" + (page + 1) + "&7/&f" + pageCount + "&8]";
        DailyRewardHolder holder = new DailyRewardHolder(category, page);
        Inventory inv = Bukkit.createInventory(holder, 54, color(title));
        holder.inventory(inv);
        fill(inv);
        List<Integer> path = snakeSlots();

        for (int i = start; i < end; i++) {
            RewardDefinition reward = rewards.get(i);
            boolean claimed = claimedStates.getOrDefault(reward.id(), false);
            boolean eligible = reward.enabled() && (reward.permission().isBlank() || player.hasPermission(reward.permission()));
            if (category.equals("playtime")) eligible &= player.hasPermission("dailyreward.playtime.bypass") || minutes >= reward.requiredMinutes();
            else eligible &= stats.currentStreak() >= milestoneDay(reward.id());
            if (claimed) eligible = false;

            List<String> lore = fancyLore(reward.lore());
            if (category.equals("playtime")) {
                long required = Math.max(1, reward.requiredMinutes());
                int percent = (int) Math.min(100, minutes * 100L / required);
                lore.add("&8&m----------------------");
                lore.add("&b⌚ &7Playtime: &f" + formatMinutes(minutes) + " &8/ &f" + formatMinutes(reward.requiredMinutes()));
                lore.add("&8[" + progressBar(percent, 14) + "&8] &f" + percent + "%");
            } else {
                int day = milestoneDay(reward.id());
                lore.add("&8&m----------------------");
                lore.add("&d✧ &7Milestone: &fDay " + day);
                lore.add("&d✦ &7Your streak: &f" + stats.currentStreak() + " day(s)");
                if (eligible) lore.add("&a✓ &7Milestone reached; reward delivered automatically.");
            }
            if (category.equals("streak")) appendStreakStatusLore(lore, claimed, eligible);
            else appendStatusLore(lore, claimed, eligible);

            Material icon = claimed ? statusMaterial("claimed", Material.LIME_STAINED_GLASS_PANE)
                    : eligible ? material(reward.material()) : statusMaterial("locked", Material.GRAY_STAINED_GLASS_PANE);
            String label = "&8#" + String.format(Locale.ROOT, "%02d", i + 1) + " &r" + reward.displayName();
            inv.setItem(path.get(i - start), createItem(icon, statusName(label, claimed, eligible), lore,
                    category.equals("playtime") && eligible ? "claim:playtime:" + reward.id() : "", eligible || claimed));
        }

        // Bottom control strip, separate from the serpentine track.
        inv.setItem(45, createItem(Material.ARROW, "&c❮ Back to Rewards", List.of("&7Return to the main menu."), "back", false));
        inv.setItem(49, createItem(category.equals("playtime") ? Material.CLOCK : Material.FIREWORK_STAR,
                category.equals("playtime") ? "&aYour Playtime" : "&dYour Streak",
                category.equals("playtime")
                        ? List.of("&8&m----------------------", "&7Total tracked: &f" + formatMinutes(minutes), "&7Milestones: &f" + rewards.size(), "&b✦ Keep going!")
                        : List.of("&8&m----------------------", "&7Current: &f" + stats.currentStreak() + " days", "&7Best: &f" + stats.bestStreak() + " days", "&d✦ Come back every day!"), "", true));
        if (page > 0) inv.setItem(48, createItem(Material.SPECTRAL_ARROW, "&ePrevious Trail", List.of("&7Return to the previous section."), "page:" + category + ":" + (page - 1), false));
        if (page + 1 < pageCount) inv.setItem(50, createItem(Material.SPECTRAL_ARROW, "&eNext Trail", List.of("&7Continue your progression."), "page:" + category + ":" + (page + 1), false));
        player.openInventory(inv);
    }

    public void handleClick(Player player, Inventory inv, int rawSlot) {
        if (!(inv.getHolder() instanceof DailyRewardHolder)) return;
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return;
        ItemStack item = inv.getItem(rawSlot);
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;
        if (action.startsWith("open:")) openCategory(player, action.substring(5));
        else if (action.startsWith("menu:")) {
            switch (action.substring(5)) {
                case "basic" -> openBasic(player);
                case "daily-reward" -> openDailyPeriods(player);
                case "supporter" -> openSupporter(player);
                case "extra" -> openExtra(player);
                case "seasonal" -> openSeasonal(player);
                case "season-winter" -> openSeasonCategory(player, "WINTER");
                case "season-christmas" -> openSeasonCategory(player, "CHRISTMAS");
                case "season-spring" -> openSeasonCategory(player, "SPRING");
                case "season-easter" -> openSeasonCategory(player, "EASTER");
                case "streak", "playtime" -> openCategory(player, action.substring(5));
            }
        }
        else if (action.startsWith("page:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
                try { openCategory(player, parts[1], Integer.parseInt(parts[2])); }
                catch (NumberFormatException ignored) { }
            }
        } else if (action.startsWith("claim:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) plugin.getClaimService().claim(player, parts[1], parts[2]);
        } else if (action.equals("back")) {
            String menu = ((DailyRewardHolder) inv.getHolder()).page();
            switch (menu) {
                case "basic", "extra", "streak", "playtime" -> openMain(player);
                case "daily-periods", "supporter" -> openBasic(player);
                case "daily", "weekly", "monthly" -> openDailyPeriods(player);
                case "events" -> openSeasonal(player);
                case "seasonal" -> openExtra(player);
                default -> openMain(player);
            }
        }
    }

    private Map<String, RewardDefinition> rewardsFor(String category) {
        return switch (category) {
            case "daily" -> plugin.getCatalog().daily();
            case "weekly" -> plugin.getCatalog().weekly();
            case "monthly" -> plugin.getCatalog().monthly();
            case "playtime" -> plugin.getCatalog().playtime();
            case "events" -> plugin.getCatalog().events();
            case "onetime" -> plugin.getCatalog().oneTime();
            case "supporter" -> plugin.getCatalog().supporter();
            case "streak" -> {
                Map<String, RewardDefinition> values = new LinkedHashMap<>();
                plugin.getCatalog().streak().forEach((day, reward) -> values.put(reward.id(), reward));
                yield values;
            }
            default -> Map.of();
        };
    }


    private void appendStreakStatusLore(List<String> lore, boolean claimed, boolean reached) {
        lore.add("&8&m----------------------");
        if (claimed) {
            lore.add("&a✔ &lCLAIMED");
            lore.add("&7This milestone is already in your collection.");
        } else if (reached) {
            lore.add("&d✦ &lMILESTONE REACHED");
            lore.add("&7Granted automatically when the daily claim reaches this day.");
        } else {
            lore.add("&8✧ &lLOCKED");
            lore.add("&7Keep your daily streak alive to unlock it.");
        }
    }

    private void appendStatusLore(List<String> lore, boolean claimed, boolean eligible) {
        lore.add("&8&m----------------------");
        if (claimed) {
            lore.add("&a✔ &lCLAIMED");
            lore.add("&7This reward is already in your collection.");
        } else if (eligible) {
            lore.add("&b✦ &lREADY TO CLAIM");
            lore.add("&e➤ Click to receive this reward.");
        } else {
            lore.add("&8✧ &lLOCKED");
            lore.add("&7Complete the requirement to unlock it.");
        }
    }

    private List<String> fancyLore(List<String> source) {
        List<String> result = new ArrayList<>();
        result.add("&8&m----------------------");
        for (String line : source) result.add("&7❖ &f" + line);
        return result;
    }

    private String statusName(String original, boolean claimed, boolean eligible) {
        if (claimed) return "&a✔ &r" + original + " &8[CLAIMED]";
        if (eligible) return "&b✦ &r" + original + " &8[READY]";
        return "&8✧ &7" + original + " &8[LOCKED]";
    }

    private Material statusMaterial(String status, Material fallback) {
        return material(gui.getString("status." + status + ".material", fallback.name()));
    }

    private void addBack(Inventory inv) {
        int slot = plugin.getConfig().getInt("gui.back-slot", inv.getSize() - 5);
        if (slot < 0 || slot >= inv.getSize()) slot = inv.getSize() - 5;
        inv.setItem(slot, createItem(material(gui.getString("buttons.back.material", "ARROW")),
                gui.getString("buttons.back.name", "&c❮ Back"), getLoreList("buttons.back"), "back", false));
    }

    private void addPagination(Inventory inv, String category, int page, int pageCount, int contentSlots) {
        int previousSlot = plugin.getConfig().getInt("gui.previous-slot", inv.getSize() - 9);
        int nextSlot = plugin.getConfig().getInt("gui.next-slot", inv.getSize() - 1);
        if (previousSlot < contentSlots || previousSlot >= inv.getSize()) previousSlot = inv.getSize() - 9;
        if (nextSlot < contentSlots || nextSlot >= inv.getSize() || nextSlot == previousSlot) nextSlot = inv.getSize() - 1;
        if (page > 0) inv.setItem(previousSlot, createItem(material(gui.getString("buttons.previous.material", "ARROW")),
                gui.getString("buttons.previous.name", "&e❮ Previous Page"), getLoreList("buttons.previous"), "page:" + category + ":" + (page - 1), false));
        if (page + 1 < pageCount) inv.setItem(nextSlot, createItem(material(gui.getString("buttons.next.material", "ARROW")),
                gui.getString("buttons.next.name", "&eNext Page ❯"), getLoreList("buttons.next"), "page:" + category + ":" + (page + 1), false));
    }

    private List<String> getLoreList(String path) {
        List<String> lore = gui.getStringList(path + ".Lore");
        return lore.isEmpty() ? gui.getStringList(path + ".lore") : lore;
    }

    private void fill(Inventory inv) {
        if (!plugin.getConfig().getBoolean("gui.filler.enabled", true)) return;
        Material[] palette = {Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
                Material.MAGENTA_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE};
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) continue;
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == 5 || col == 0 || col == 8;
            Material pane = edge ? palette[(row + col) % palette.length] : palette[0];
            inv.setItem(i, createItem(pane, "&8✦", List.of(), "", false));
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore, String action, boolean glow) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.CHEST : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(id.tempestaria.dailyreward.service.ActionService.component(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(id.tempestaria.dailyreward.service.ActionService::component).toList());
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (glow) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<Integer> snakeSlots() {
        // A narrow, winding trail with breathing room for the jewel-glass background.
        // The route follows the numbered path: down, across, up, across, down, and back.
        int[][] path = {
                {0,1},{1,1},{2,1},{3,1},{4,1},
                {4,2},{4,3},{3,3},{2,3},{1,3},{0,3},
                {0,4},{0,5},{1,5},{2,5},{3,5},{4,5},
                {4,6},{4,7},{3,7},{2,7},{1,7},{0,7}
        };
        List<Integer> slots = new ArrayList<>(path.length);
        for (int[] point : path) slots.add(point[0] * 9 + point[1]);
        return slots;
    }

    private int firstFreeMainSlot(Set<Integer> occupied, int limit, int reserved) {
        // Prefer the central menu area before falling back to the outer edges.
        int[] preferred = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 9, 17, 18, 26};
        for (int slot : preferred) if (slot < limit && slot != reserved && !occupied.contains(slot)) return slot;
        for (int slot = 0; slot < limit; slot++) if (slot != reserved && !occupied.contains(slot)) return slot;
        return -1;
    }

    private int firstFreeSlot(Set<Integer> occupied, int limit) {
        for (int slot = 0; slot < limit; slot++) if (!occupied.contains(slot)) return slot;
        return -1;
    }

    private int milestoneDay(String id) {
        try { return Integer.parseInt(id.substring(id.lastIndexOf('-') + 1)); }
        catch (Exception ignored) { return Integer.MAX_VALUE; }
    }

    private String claimType(String category) {
        return switch (category) {
            case "weekly" -> "WEEKLY";
            case "monthly" -> "MONTHLY";
            case "supporter" -> "SUPPORTER";
            default -> "DAILY";
        };
    }

    private String currentPeriod(String category) {
        if (category.equals("supporter")) return "once";
        ZoneId zone = zone();
        LocalTime reset;
        try { reset = LocalTime.parse(plugin.getConfig().getString("time.daily-reset", "00:00")); }
        catch (Exception ignored) { reset = LocalTime.MIDNIGHT; }
        return RewardPeriodUtil.key(Instant.now(), zone, reset, category);
    }

    private ZoneId zone() {
        try { return ZoneId.of(plugin.getConfig().getString("time.timezone", "Asia/Jakarta")); }
        catch (Exception ignored) { return ZoneId.of("Asia/Jakarta"); }
    }

    private boolean withinWindow(String start, String end) {
        Instant now = Instant.now();
        try { if (start != null && !start.isBlank() && now.isBefore(OffsetDateTime.parse(start).toInstant())) return false; }
        catch (Exception ignored) { return false; }
        try { if (end != null && !end.isBlank() && now.isAfter(OffsetDateTime.parse(end).toInstant())) return false; }
        catch (Exception ignored) { return false; }
        return true;
    }

    private String dateLabel(String value) {
        if (value == null || value.isBlank()) return "Open-ended";
        try { return OffsetDateTime.parse(value).toLocalDate().toString(); }
        catch (Exception ignored) { return value; }
    }

    private String formatMinutes(long minutes) {
        long safe = Math.max(0, minutes);
        long hours = safe / 60;
        long remaining = safe % 60;
        return hours > 0 ? hours + "h " + remaining + "m" : remaining + "m";
    }

    private String progressBar(int percent, int length) {
        int filled = Math.max(0, Math.min(length, percent * length / 100));
        return "&b" + "▰".repeat(filled) + "&8" + "▱".repeat(length - filled);
    }

    private String categoryName(String category) {
        return switch (category) {
            case "daily" -> "Daily Rewards";
            case "weekly" -> "Weekly Rewards";
            case "monthly" -> "Monthly Rewards";
            case "playtime" -> "Playtime Trail";
            case "streak" -> "Daily Streak Trail";
            case "events" -> "Seasonal Events";
            case "onetime" -> "One-Time Rewards";
            case "supporter" -> "Supporter Rank Rewards";
            default -> category;
        };
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : lower.split("\\s+")) {
            if (!word.isBlank()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    private String defaultIcon(String category) {
        return switch (category) {
            case "daily" -> "CHEST";
            case "weekly" -> "ENDER_CHEST";
            case "monthly" -> "NETHER_STAR";
            case "streak" -> "FIREWORK_STAR";
            case "playtime" -> "CLOCK";
            case "events" -> "SNOW_BLOCK";
            default -> "EMERALD";
        };
    }

    private static Material material(String name) {
        Material found = Material.matchMaterial(name == null ? "CHEST" : name);
        return found == null ? Material.CHEST : found;
    }

    private static String color(String value) { return value.replace('&', '§'); }
    private static int validSize(int size) { return Math.max(9, Math.min(54, (int) Math.ceil(size / 9.0) * 9)); }
}
