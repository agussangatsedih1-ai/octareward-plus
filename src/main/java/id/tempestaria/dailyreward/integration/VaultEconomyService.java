package id.tempestaria.dailyreward.integration;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional Vault Economy adapter.
 *
 * VaultAPI is loaded from the installed Vault plugin at runtime, so this
 * project does not need to download or bundle VaultAPI during compilation.
 */
public final class VaultEconomyService implements EconomyService {
    private final DailyRewardPlugin plugin;
    private final Object economy;
    private final Class<?> economyClass;

    public VaultEconomyService(DailyRewardPlugin plugin) {
        this.plugin = plugin;
        Object provider = null;
        Class<?> apiClass = null;

        try {
            Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
            if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
                throw new IllegalStateException("Vault plugin is not enabled");
            }

            ClassLoader vaultClassLoader = vaultPlugin.getClass().getClassLoader();
            apiClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vaultClassLoader);

            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration((Class) apiClass);

            if (registration != null) {
                provider = registration.getProvider();
            }
            if (provider == null) {
                throw new IllegalStateException("No Vault economy provider is registered");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not connect to Vault Economy: " + ex.getMessage());
            provider = null;
            apiClass = null;
        }

        this.economy = provider;
        this.economyClass = apiClass;
    }

    @Override
    public boolean isAvailable() {
        return economy != null && economyClass != null;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!isAvailable() || player == null || !player.isOnline()
                || !Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }

        try {
            Object response = economyClass
                    .getMethod("depositPlayer", Player.class, double.class)
                    .invoke(economy, player, amount);

            boolean success = (boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
            if (!success) {
                plugin.getLogger().warning("Vault deposit failed for " + player.getName()
                        + ": " + readErrorMessage(response));
            }
            return success;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Vault deposit failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) return 0.0;
        try {
            Object result = economyClass
                    .getMethod("getBalance", OfflinePlayer.class)
                    .invoke(economy, player);
            return result instanceof Number number ? number.doubleValue() : 0.0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not read Vault balance: " + ex.getMessage());
            return 0.0;
        }
    }

    @Override
    public String format(double amount) {
        if (!isAvailable()) return String.valueOf(amount);
        try {
            Object result = economyClass.getMethod("format", double.class).invoke(economy, amount);
            return result == null ? String.valueOf(amount) : result.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not format Vault currency: " + ex.getMessage());
            return String.valueOf(amount);
        }
    }

    private String readErrorMessage(Object response) {
        if (response == null) return "Unknown Vault error";
        try {
            Field field = response.getClass().getField("errorMessage");
            Object value = field.get(response);
            return value == null ? "Unknown Vault error" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "Vault transaction was not successful";
        }
    }
}
