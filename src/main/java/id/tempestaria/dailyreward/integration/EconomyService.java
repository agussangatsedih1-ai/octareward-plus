package id.tempestaria.dailyreward.integration;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Optional economy abstraction; the implementation is supplied by the Vault adapter when available. */
public interface EconomyService {
    boolean isAvailable();
    boolean deposit(Player player, double amount);
    double getBalance(OfflinePlayer player);
    String format(double amount);
}
