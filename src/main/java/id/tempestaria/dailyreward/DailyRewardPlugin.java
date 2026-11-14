package id.tempestaria.dailyreward;

import id.tempestaria.dailyreward.api.DailyRewardAPI;
import id.tempestaria.dailyreward.command.DailyRewardCommand;
import id.tempestaria.dailyreward.config.RewardCatalog;
import id.tempestaria.dailyreward.db.DatabaseManager;
import id.tempestaria.dailyreward.gui.GuiManager;
import id.tempestaria.dailyreward.integration.UltimateRewardsBridge;
import id.tempestaria.dailyreward.integration.EconomyService;
import id.tempestaria.dailyreward.listener.PlayerListener;
import id.tempestaria.dailyreward.service.ActionService;
import id.tempestaria.dailyreward.service.ClaimService;
import id.tempestaria.dailyreward.service.PlaytimeService;
import id.tempestaria.dailyreward.service.ReminderService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class DailyRewardPlugin extends JavaPlugin {
    private DatabaseManager database;
    private RewardCatalog catalog;
    private ActionService actionService;
    private PlaytimeService playtimeService;
    private ClaimService claimService;
    private GuiManager guiManager;
    private DailyRewardAPI api;
    private YamlConfiguration messages;
    private final java.util.List<Object> placeholderExpansions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private EconomyService economyService;
    private ReminderService reminderService;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("messages.yml"); saveIfMissing("gui.yml"); saveIfMissing("storage.yml");
        for (String path : new String[]{"rewards/daily.yml", "rewards/weekly.yml", "rewards/monthly.yml", "rewards/streak.yml", "rewards/playtime.yml", "rewards/events.yml", "rewards/seasonal-events.yml", "rewards/one-time.yml", "rewards/supporter.yml"}) saveIfMissing(path);
        loadMessages();

        database = new DatabaseManager(this);
        try { database.initialize(); }
        catch (Exception ex) {
            getLogger().severe("Could not initialize storage: " + ex.getMessage());
            Bukkit.getPluginManager().disablePlugin(this); return;
        }

        catalog = new RewardCatalog(this); catalog.load();
        syncScheduledSeasonalEvents();
        actionService = new ActionService(this);
        playtimeService = new PlaytimeService(this, database);
        claimService = new ClaimService(this, database);
        guiManager = new GuiManager(this);
        api = new DailyRewardAPI(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        DailyRewardCommand executor = new DailyRewardCommand(this);
        if (getCommand("dailyreward") != null) {
            getCommand("dailyreward").setExecutor(executor);
            getCommand("dailyreward").setTabCompleter(executor);
        }

        long interval = Math.max(20L, getConfig().getLong("autosave.interval-seconds", 60) * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> playtimeService.sampleOnline(), interval, interval);
        reminderService = new ReminderService(this);
        reminderService.start();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) registerPlaceholderExpansions();
        // Wait until all plugins have completed onEnable so economy providers can register with Vault.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (isEnabled()) registerVaultIntegration();
        }, 1L);

        if (getConfig().getBoolean("integrations.ultimaterewards.enabled", true)) {
            new UltimateRewardsBridge(this).enable();
            if (!isEnabled()) return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) playtimeService.join(online);
        getLogger().info("OctaReward Plus v" + getDescription().getVersion() + " enabled. Storage=" + getConfig().getString("storage.type", "SQLITE"));
    }

    @Override public void onDisable() {
        if (playtimeService != null) playtimeService.flushAll();
        if (reminderService != null) reminderService.clear();
        for (Object expansion : java.util.List.copyOf(placeholderExpansions)) {
            try { expansion.getClass().getMethod("unregister").invoke(expansion); }
            catch (ReflectiveOperationException ex) { getLogger().fine("Could not unregister PlaceholderAPI expansion cleanly: " + ex.getMessage()); }
        }
        placeholderExpansions.clear();
        if (database != null) database.close();
        getLogger().info("OctaReward Plus disabled.");
    }

    private void syncScheduledSeasonalEvents() {
        if (database == null || catalog == null) return;
        for (var reward : catalog.events().values()) {
            if (!reward.enabled() || !reward.autoStart() || reward.season().isBlank()
                    || reward.startsAt().isBlank() || reward.endsAt().isBlank()) continue;
            try {
                long start = java.time.OffsetDateTime.parse(reward.startsAt()).toInstant().toEpochMilli();
                long end = java.time.OffsetDateTime.parse(reward.endsAt()).toInstant().toEpochMilli();
                if (end <= start) throw new IllegalArgumentException("ends-at must be after starts-at");
                database.ensureScheduledEvent(reward.id(), start, end).exceptionally(error -> {
                    getLogger().warning("Could not schedule seasonal event '" + reward.id() + "': " + error.getMessage());
                    return null;
                });
            } catch (Exception ex) {
                getLogger().warning("Invalid seasonal schedule for '" + reward.id() + "': " + ex.getMessage());
            }
        }
    }

    private void saveIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) saveResource(path, false);
    }

    private void loadMessages() { messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")); }

    public void reloadPluginConfig() {
        reloadConfig(); loadMessages();
        if (catalog != null) { catalog.load(); syncScheduledSeasonalEvents(); }
        if (guiManager != null) guiManager.load();
        if (playtimeService != null) playtimeService.reloadSettings();
        invalidateAllPlaceholderStates();
        getLogger().warning("Config/reward files reloaded. Storage connection settings require a full server restart to apply.");
    }

    public void message(Player player, String key) {
        String prefix = messages == null ? "&8[&bOctaReward Plus&8] &r" : messages.getString("prefix", "&8[&bOctaReward Plus&8] &r");
        String body = messages == null ? "&c" + key : messages.getString(key, "&c" + key);
        player.sendMessage(ActionService.component(prefix + body));
    }

    public static String color(String text) { return text.replace('&', '§'); }

    public DatabaseManager getDatabase() { return database; }
    public RewardCatalog getCatalog() { return catalog; }
    public ActionService getActionService() { return actionService; }
    public PlaytimeService getPlaytimeService() { return playtimeService; }
    public ClaimService getClaimService() { return claimService; }
    public GuiManager getGuiManager() { return guiManager; }
    public DailyRewardAPI getApi() { return api; }
    public YamlConfiguration getMessages() { return messages; }
    public EconomyService getEconomyService() { return economyService; }
    public ReminderService getReminderService() { return reminderService; }

    private void registerPlaceholderExpansions() {
        try {
            Class<?> type = Class.forName("id.tempestaria.dailyreward.placeholder.DailyRewardExpansion", true, getClass().getClassLoader());
            var constructor = type.getConstructor(DailyRewardPlugin.class, String.class);
            for (String identifier : java.util.List.of("octarewardplus", "dailyreward")) {
                Object expansion = constructor.newInstance(this, identifier);
                Object registered = type.getMethod("register").invoke(expansion);
                if (Boolean.TRUE.equals(registered)) {
                    placeholderExpansions.add(expansion);
                    getLogger().info("PlaceholderAPI expansion registered: %" + identifier + "_%");
                } else {
                    getLogger().warning("PlaceholderAPI expansion could not register for namespace: " + identifier);
                }
            }
        } catch (Exception ex) {
            getLogger().warning("PlaceholderAPI expansion could not register: " + ex.getMessage());
        }
    }

    private void registerVaultIntegration() {
        if (!getConfig().getBoolean("integrations.vault.enabled", true)) return;
        boolean vaultEnabled = Bukkit.getPluginManager().isPluginEnabled("Vault");
        if (!vaultEnabled) {
            String message = "Vault is not installed/enabled; [money] reward actions are unavailable.";
            if (getConfig().getBoolean("integrations.vault.required", false)) {
                getLogger().severe(message + " Vault is required by config; disabling OctaReward Plus.");
                Bukkit.getPluginManager().disablePlugin(this);
            } else {
                getLogger().info(message);
            }
            return;
        }
        try {
            Class<?> type = Class.forName("id.tempestaria.dailyreward.integration.VaultEconomyService", true, getClass().getClassLoader());
            Object adapter = type.getConstructor(DailyRewardPlugin.class).newInstance(this);
            economyService = (EconomyService) adapter;
            if (!economyService.isAvailable()) {
                String message = "Vault is loaded, but no economy provider is registered; [money] actions are unavailable.";
                if (getConfig().getBoolean("integrations.vault.required", false)) {
                    getLogger().severe(message + " Economy is required by config; disabling OctaReward Plus.");
                    Bukkit.getPluginManager().disablePlugin(this);
                } else {
                    getLogger().warning(message);
                }
            } else {
                getLogger().info("Vault Economy integration enabled: " + economyService.format(100.0));
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning("Vault Economy integration could not be initialized: " + ex.getMessage());
        }
    }

    public void invalidatePlaceholderState(java.util.UUID uuid) {
        invokePlaceholderMethod("invalidate", new Class<?>[]{java.util.UUID.class}, new Object[]{uuid});
    }

    public void invalidateAllPlaceholderStates() {
        invokePlaceholderMethod("invalidateAll", new Class<?>[0], new Object[0]);
    }

    private void invokePlaceholderMethod(String method, Class<?>[] types, Object[] args) {
        for (Object expansion : placeholderExpansions) {
            try { expansion.getClass().getMethod(method, types).invoke(expansion, args); }
            catch (Exception ex) { getLogger().fine("Placeholder cache invalidation failed: " + ex.getMessage()); }
        }
    }
}
