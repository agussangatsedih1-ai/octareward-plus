package id.tempestaria.dailyreward.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DailyRewardStreakChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int previous;
    private final int current;
    public DailyRewardStreakChangeEvent(Player player, int previous, int current) {
        this.player = player; this.previous = previous; this.current = current;
    }
    public Player getPlayer() { return player; }
    public int getPreviousStreak() { return previous; }
    public int getCurrentStreak() { return current; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
