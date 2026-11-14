package id.tempestaria.dailyreward.integration;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.util.PlaytimeMath;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.io.File;
import java.lang.reflect.*;
import java.net.URI;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Optional, reflection-only bridge. No UltimateRewards classes are linked at compile time. */
public final class UltimateRewardsBridge {
    private final DailyRewardPlugin plugin;
    private Plugin ultimateRewards;
    private Class<?> apiClass;

    public UltimateRewardsBridge(DailyRewardPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        ultimateRewards = Bukkit.getPluginManager().getPlugin("UltimateRewards");
        if (ultimateRewards == null || !ultimateRewards.isEnabled()) {
            if (plugin.getConfig().getBoolean("integrations.ultimaterewards.required", false)) {
                plugin.getLogger().severe("UltimateRewards is required by config, but it is not installed/enabled. Disabling DailyReward.");
                Bukkit.getPluginManager().disablePlugin(plugin);
            } else plugin.getLogger().info("UltimateRewards not detected; standalone mode enabled.");
            return;
        }
        try {
            apiClass = findClassBySimpleName("UltimateReward");
            if (apiClass == null) {
                plugin.getLogger().warning("UltimateRewards API class 'UltimateReward' not found in its JAR; lookup integration disabled.");
                if (plugin.getConfig().getBoolean("integrations.ultimaterewards.required", false)) {
                    plugin.getLogger().severe("UltimateRewards is required, but its documented API class could not be located. Disabling DailyReward.");
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    return;
                }
            } else plugin.getLogger().info("UltimateRewards API class located: " + apiClass.getName());
            if (plugin.getConfig().getBoolean("integrations.ultimaterewards.listen-events", true)) registerEvents();
            if (plugin.getConfig().getBoolean("integrations.ultimaterewards.custom-playtime-calculator.enabled", false)) registerCalculator();
            String lookupName = plugin.getConfig().getString("integrations.ultimaterewards.lookup-example-reward", "");
            if (apiClass != null && !lookupName.isBlank()) lookupReward(lookupName);
        } catch (Exception ex) {
            plugin.getLogger().severe("UltimateRewards bridge could not initialize safely: " + ex.getMessage());
            if (plugin.getConfig().getBoolean("integrations.ultimaterewards.required", false)) Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    private Class<?> findClassBySimpleName(String simpleName) throws Exception {
        for (String className : classNames()) {
            if (className.endsWith("." + simpleName)) {
                try { return Class.forName(className, false, ultimateRewards.getClass().getClassLoader()); }
                catch (ClassNotFoundException ignored) { }
            }
        }
        return null;
    }

    private List<String> classNames() throws Exception {
        File jar = new File(ultimateRewards.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> result = new ArrayList<>();
        if (!jar.isFile()) return result;
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.contains("$")) result.add(name.substring(0, name.length() - 6).replace('/', '.'));
            }
        }
        return result;
    }

    private void lookupReward(String name) {
        try {
            boolean found = false;
            try {
                Method method = apiClass.getMethod("getRewardByName", String.class);
                Object result = method.invoke(null, name);
                found = result instanceof Optional<?> optional && optional.isPresent();
            } catch (NoSuchMethodException missingLookup) {
                plugin.getLogger().fine("getRewardByName(String) unavailable; trying documented isReward(String).");
            }
            if (!found) {
                try {
                    Method existsMethod = apiClass.getMethod("isReward", String.class);
                    Object exists = existsMethod.invoke(null, name);
                    if (exists instanceof Boolean value) found = value;
                } catch (NoSuchMethodException missingCheck) {
                    plugin.getLogger().warning("UltimateRewards exposes neither documented lookup signature in this JAR.");
                }
            }
            plugin.getLogger().info("UltimateRewards reward lookup '" + name + "': " + (found ? "FOUND" : "not found"));
        } catch (Exception ex) {
            plugin.getLogger().warning("UltimateRewards reward lookup failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerEvents() {
        String base = "eu.athelion.ultimaterewards.api.event.";
        List<String> names = List.of("PlayerPreClaimRewardEvent", "PlayerClaimedRewardEvent", "ReminderReceiveEvent",
                "UserLoadedEvent", "UserUnloadEvent", "ReferralCreateEvent", "ReferralApplyEvent");
        Listener listener = new Listener() {};
        for (String simple : names) {
            try {
                Class<?> raw = Class.forName(base + simple, false, ultimateRewards.getClass().getClassLoader());
                if (!Event.class.isAssignableFrom(raw)) {
                    plugin.getLogger().warning("Skipping unexpected UltimateRewards event type: " + raw.getName()); continue;
                }
                Class<? extends Event> eventClass = (Class<? extends Event>) raw;
                EventExecutor executor = (ignored, event) -> handleEvent(simple, event);
                Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin, true);
                plugin.getLogger().fine("Registered UltimateRewards listener: " + simple);
            } catch (ClassNotFoundException ex) {
                plugin.getLogger().fine("UltimateRewards event unavailable in this version: " + simple);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not register UltimateRewards event " + simple + ": " + ex.getMessage());
            }
        }
    }

    private void handleEvent(String eventName, Event event) {
        try {
            if (eventName.equals("PlayerClaimedRewardEvent")) {
                Object claimer = invokeNoArg(event, "getClaimer");
                Object reward = invokeNoArg(event, "getClaimedReward");
                plugin.getLogger().fine("UltimateRewards claim completed: player=" + safe(claimer) + ", reward=" + safe(reward));
            } else if (eventName.equals("PlayerPreClaimRewardEvent")) {
                Object claimer = invokeNoArg(event, "getClaimer");
                plugin.getLogger().fine("UltimateRewards pre-claim observed for " + safe(claimer) + ". No actions were modified.");
            } else if (eventName.equals("ReminderReceiveEvent")) {
                // Reminder integration is intentionally passive; this bridge does not suppress reminders.
            } else if (eventName.equals("UserLoadedEvent") || eventName.equals("UserUnloadEvent")) {
                // Do not touch Bukkit API or player state here: UserLoadedEvent may be asynchronous.
                plugin.getLogger().fine("UltimateRewards user lifecycle event observed: " + eventName + ", async=" + event.isAsynchronous());
            }
        } catch (Exception ex) { plugin.getLogger().fine("Could not inspect UltimateRewards event " + eventName + ": " + ex.getMessage()); }
    }

    private Object invokeNoArg(Object target, String method) {
        try { return target == null ? null : target.getClass().getMethod(method).invoke(target); }
        catch (Exception ignored) { return null; }
    }

    private String safe(Object value) { return value == null ? "unknown" : String.valueOf(value); }

    private void registerCalculator() {
        try {
            Method registration = Arrays.stream(apiClass.getMethods())
                    .filter(m -> m.getName().equals("registerPlayTimeCalculator") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1)
                    .findFirst().orElseThrow(() -> new NoSuchMethodException("registerPlayTimeCalculator(one interface)"));
            Class<?> calculatorType = registration.getParameterTypes()[0];
            if (!calculatorType.isInterface()) throw new IllegalStateException("PlayTimeCalculator parameter is not an interface in this JAR.");
            if (!calculatorType.getSimpleName().equals("PlayTimeCalculator"))
                throw new IllegalStateException("Unexpected calculator interface: " + calculatorType.getName());
            Method calculateMethod = calculatorType.getMethod("calculatePlayTime", OfflinePlayer.class);
            if (calculateMethod.getReturnType() != float.class && calculateMethod.getReturnType() != Float.class)
                throw new IllegalStateException("calculatePlayTime(OfflinePlayer) does not return float in this JAR.");
            Object proxy = Proxy.newProxyInstance(calculatorType.getClassLoader(), new Class<?>[]{calculatorType}, (object, method, args) -> {
                if (method.getName().equals("calculatePlayTime") && args != null && args.length == 1) {
                    Object offlinePlayer = args[0];
                    long minutes;
                    String source = plugin.getConfig().getString("playtime.source", "DAILYREWARD_TRACKER").toUpperCase(Locale.ROOT);
                    if (source.equals("MINECRAFT_STATISTIC") && offlinePlayer instanceof OfflinePlayer offline) {
                        minutes = Math.max(0, PlaytimeMath.ticksToMinutes(offline.getStatistic(Statistic.PLAY_ONE_MINUTE)));
                    } else if (offlinePlayer instanceof OfflinePlayer offline) {
                        minutes = plugin.getPlaytimeService().cachedMinutes(offline.getUniqueId());
                    } else minutes = 0L;
                    return (float) minutes;
                }
                if (method.getName().equals("toString")) return "DailyRewardPlayTimeCalculator";
                if (method.getName().equals("hashCode")) return System.identityHashCode(object);
                if (method.getName().equals("equals")) return object == args[0];
                return null;
            });
            registration.invoke(null, proxy);
            plugin.getLogger().warning("DailyReward custom playtime calculator registered into UltimateRewards. This changes UltimateRewards' playtime calculation globally; disable it unless intentionally desired.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Custom UltimateRewards playtime calculator not registered: " + ex.getMessage());
        }
    }
}
