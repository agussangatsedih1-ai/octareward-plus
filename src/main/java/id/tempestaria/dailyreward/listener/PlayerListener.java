package id.tempestaria.dailyreward.listener;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.gui.DailyRewardHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final DailyRewardPlugin plugin;
    public PlayerListener(DailyRewardPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void onJoin(PlayerJoinEvent event) { plugin.getPlaytimeService().join(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        plugin.getPlaytimeService().quit(event.getPlayer());
        if (plugin.getReminderService() != null) plugin.getReminderService().onQuit(event.getPlayer());
        plugin.invalidatePlaceholderState(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onBreak(BlockBreakEvent event) {
        plugin.getClaimService().onBukkitTrigger(event.getPlayer(), "BLOCK_BREAK", event.getBlock().getType().name(), event.getPlayer().getWorld().getName());
    }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) plugin.getClaimService().onBukkitTrigger(killer, "ENTITY_KILL", event.getEntity().getType().name(), killer.getWorld().getName());
    }
    @EventHandler public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) plugin.getClaimService().onBukkitTrigger(event.getPlayer(), "LEVEL_UP", String.valueOf(event.getNewLevel()), event.getPlayer().getWorld().getName());
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DailyRewardHolder)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory())
            plugin.getGuiManager().handleClick(player, event.getView().getTopInventory(), event.getRawSlot());
    }

    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DailyRewardHolder) event.setCancelled(true);
    }
}
