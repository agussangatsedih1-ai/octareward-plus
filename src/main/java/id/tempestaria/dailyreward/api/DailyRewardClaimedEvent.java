package id.tempestaria.dailyreward.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DailyRewardClaimedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String category;
    private final String rewardId;
    public DailyRewardClaimedEvent(Player player, String category, String rewardId) {
        this.player = player; this.category = category; this.rewardId = rewardId;
    }
    public Player getPlayer() { return player; }
    public String getCategory() { return category; }
    public String getRewardId() { return rewardId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
