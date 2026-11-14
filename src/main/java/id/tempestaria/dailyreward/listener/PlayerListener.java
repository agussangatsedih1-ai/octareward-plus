package id.tempestaria.dailyreward.listener;

import id.tempestaria.dailyreward.DailyRewardPlugin;
import id.tempestaria.dailyreward.gui.DailyRewardHolder;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

public final class PlayerListener implements Listener {
    private final DailyRewardPlugin plugin;
    public PlayerListener(DailyRewardPlugin plugin) { this.plugin = plugin; }

    @EventHandler public void onJoin(PlayerJoinEvent event) { plugin.getPlaytimeService().join(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.getPlaytimeService().quit(event.getPlayer()); }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) plugin.getPlaytimeService().activity(event.getPlayer());
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) { plugin.getPlaytimeService().activity(event.getPlayer()); }
    @EventHandler public void onBreak(BlockBreakEvent event) {
        plugin.getPlaytimeService().activity(event.getPlayer());
        plugin.getClaimService().onBukkitTrigger(event.getPlayer(), "BLOCK_BREAK", event.getBlock().getType().name(), event.getPlayer().getWorld().getName());
    }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) plugin.getClaimService().onBukkitTrigger(killer, "ENTITY_KILL", event.getEntity().getType().name(), killer.getWorld().getName());
    }
    @EventHandler public void onLevelChange(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() > event.getOldLevel()) plugin.getClaimService().onBukkitTrigger(event.getPlayer(), "LEVEL_UP", String.valueOf(event.getNewLevel()), event.getPlayer().getWorld().getName());
    }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) { plugin.getPlaytimeService().activity(event.getPlayer()); }
    @EventHandler public void onChat(AsyncChatEvent event) { plugin.getPlaytimeService().activity(event.getPlayer()); }

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
