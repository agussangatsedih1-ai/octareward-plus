package id.tempestaria.dailyreward.config;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class RewardCatalog {
    private final DailyRewardPlugin plugin;
    private final Map<String, RewardDefinition> daily = new LinkedHashMap<>();
    private final Map<Integer, RewardDefinition> streak = new TreeMap<>();
    private final Map<String, RewardDefinition> playtime = new LinkedHashMap<>();
    private final Map<String, RewardDefinition> events = new LinkedHashMap<>();
    private final Map<String, RewardDefinition> oneTime = new LinkedHashMap<>();
    private String missedDayPolicy = "RESET";
    private int graceDays = 1;

    public RewardCatalog(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public void load() {
        daily.clear(); streak.clear(); playtime.clear(); events.clear(); oneTime.clear();
        YamlConfiguration d = loadFile("rewards/daily.yml");
        readRewards(d, "rewards", daily, false);

        YamlConfiguration s = loadFile("rewards/streak.yml");
        missedDayPolicy = s.getString("missed-day-policy", "RESET").toUpperCase(Locale.ROOT);
        if (!Set.of("RESET", "PAUSE", "GRACE_PERIOD").contains(missedDayPolicy)) missedDayPolicy = "RESET";
        graceDays = Math.max(0, s.getInt("grace-days", 1));
        ConfigurationSection milestones = s.getConfigurationSection("milestones");
        if (milestones != null) for (String key : milestones.getKeys(false)) {
            try {
                int day = Integer.parseInt(key);
                ConfigurationSection sec = milestones.getConfigurationSection(key);
                if (sec != null) streak.put(day, readDefinition("streak-" + day, sec, false));
            } catch (NumberFormatException ex) { plugin.getLogger().warning("Invalid streak milestone key: " + key); }
        }

        readRewards(loadFile("rewards/playtime.yml"), "rewards", playtime, false);
        readRewards(loadFile("rewards/events.yml"), "events", events, true);
        readRewards(loadFile("rewards/one-time.yml"), "rewards", oneTime, false);
    }

    private YamlConfiguration loadFile(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void readRewards(YamlConfiguration yaml, String root, Map<String, RewardDefinition> target, boolean event) {
        ConfigurationSection sec = yaml.getConfigurationSection(root);
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection reward = sec.getConfigurationSection(id);
            if (reward != null) target.put(id, readDefinition(id, reward, event));
        }
    }

    private RewardDefinition readDefinition(String id, ConfigurationSection sec, boolean event) {
        List<String> lore = sec.getStringList("Lore");
        if (lore.isEmpty()) lore = sec.getStringList("lore");
        return new RewardDefinition(
                id,
                sec.getString("display-name", "&f" + id),
                sec.getString("material", "CHEST"),
                sec.getInt("slot", 22),
                sec.getString("permission", ""),
                lore,
                sec.getStringList("actions"),
                sec.getLong("required-minutes", 0),
                sec.getBoolean("repeat", false),
                sec.getLong("repeat-interval-minutes", 0),
                event ? sec.getString("trigger.id", id) : "",
                event ? sec.getString("trigger.type", "CUSTOM").toUpperCase(Locale.ROOT) : "CUSTOM",
                event ? sec.getString("trigger.target", "") : "",
                event ? sec.getString("trigger.world", "") : "",
                event ? sec.getInt("trigger.required-count", 1) : 1,
                sec.getInt("availability.claim-limit-per-player", 1),
                sec.getBoolean("first-join-only", false),
                sec.getString(event ? "availability.starts-at" : "starts-at", ""),
                sec.getString(event ? "availability.ends-at" : "ends-at", ""),
                sec.getBoolean("enabled", true)
        );
    }

    public Map<String, RewardDefinition> daily() { return Collections.unmodifiableMap(daily); }
    public Map<Integer, RewardDefinition> streak() { return Collections.unmodifiableMap(streak); }
    public Map<String, RewardDefinition> playtime() { return Collections.unmodifiableMap(playtime); }
    public Map<String, RewardDefinition> events() { return Collections.unmodifiableMap(events); }
    public Map<String, RewardDefinition> oneTime() { return Collections.unmodifiableMap(oneTime); }
    public String missedDayPolicy() { return missedDayPolicy; }
    public int graceDays() { return graceDays; }

    public Optional<RewardDefinition> find(String id) {
        return all().stream().filter(r -> r.id().equalsIgnoreCase(id)).findFirst();
    }
    public Optional<RewardDefinition> findEvent(String id) {
        return events.values().stream().filter(r -> r.id().equalsIgnoreCase(id)
                || (!r.eventId().isBlank() && r.eventId().equalsIgnoreCase(id))).findFirst();
    }
    public List<RewardDefinition> all() {
        List<RewardDefinition> result = new ArrayList<>();
        result.addAll(daily.values()); result.addAll(streak.values()); result.addAll(playtime.values()); result.addAll(events.values()); result.addAll(oneTime.values());
        return result;
    }
}
