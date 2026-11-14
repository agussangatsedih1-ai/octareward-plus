package id.tempestaria.dailyreward.config;

import java.util.List;

public record RewardDefinition(String id, String displayName, String material, int slot,
                               String permission, List<String> lore, List<String> actions,
                               long requiredMinutes, boolean repeat, long repeatIntervalMinutes,
                               String eventId, String triggerType, String triggerTarget, String triggerWorld,
                               int triggerRequiredCount, int claimLimit, boolean firstJoinOnly,
                               String startsAt, String endsAt, boolean enabled,
                               String eventName, String season, boolean autoStart) {
    public RewardDefinition {
        lore = lore == null ? List.of() : List.copyOf(lore);
        actions = actions == null ? List.of() : List.copyOf(actions);
        permission = permission == null ? "" : permission;
        eventId = eventId == null ? "" : eventId;
        triggerType = triggerType == null ? "CUSTOM" : triggerType;
        triggerTarget = triggerTarget == null ? "" : triggerTarget;
        triggerWorld = triggerWorld == null ? "" : triggerWorld;
        eventName = eventName == null ? "" : eventName;
        season = season == null ? "" : season.toUpperCase(java.util.Locale.ROOT);
        triggerRequiredCount = Math.max(1, triggerRequiredCount);
    }
}
