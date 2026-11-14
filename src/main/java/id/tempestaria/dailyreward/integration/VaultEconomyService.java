package id.tempestaria.dailyreward.integration;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault Economy adapter. VaultAPI is provided by the server and is never shaded into this plugin. */
public final class VaultEconomyService implements EconomyService {
    private final DailyRewardPlugin plugin;
    private final Economy economy;

    public VaultEconomyService(DailyRewardPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!isAvailable() || player == null || !player.isOnline()
                || !Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Vault deposit failed for " + player.getName()
                    + ": " + response.errorMessage);
            return false;
        }
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) return 0.0;
        return economy.getBalance(player);
    }

    @Override
    public String format(double amount) {
        return isAvailable() ? economy.format(amount) : String.valueOf(amount);
    }
}
