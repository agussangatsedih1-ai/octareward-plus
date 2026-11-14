package id.tempestaria.dailyreward.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DailyRewardEventTriggeredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String eventId;
    private final String instanceId;
    private final Player target;
    public DailyRewardEventTriggeredEvent(String eventId, String instanceId, Player target) {
        this.eventId = eventId; this.instanceId = instanceId; this.target = target;
    }
    public String getEventId() { return eventId; }
    public String getInstanceId() { return instanceId; }
    /** Null when the event instance is server-wide. */
    public Player getTarget() { return target; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
