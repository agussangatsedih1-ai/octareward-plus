package id.tempestaria.dailyreward.db;

public record ClaimResult(boolean success, boolean duplicate, boolean coolingDown,
                          int previousStreak, int currentStreak, int bestStreak,
                          boolean streakMilestone, String claimPeriodKey, long nextAvailableAt) {
    public static ClaimResult duplicate(int current, int best) {
        return new ClaimResult(false, true, false, current, current, best, false, "", 0L);
    }

    public static ClaimResult cooldown(int current, int best, long nextAvailableAt) {
        return new ClaimResult(false, false, true, current, current, best, false, "", nextAvailableAt);
    }

    public static ClaimResult failed() {
        return new ClaimResult(false, false, false, 0, 0, 0, false, "", 0L);
    }
}
