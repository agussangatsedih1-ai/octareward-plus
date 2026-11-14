package id.tempestaria.dailyreward.db;

public record ClaimResult(boolean success, boolean duplicate, int previousStreak, int currentStreak,
                          int bestStreak, boolean streakMilestone) {
    public static ClaimResult duplicate(int current, int best) { return new ClaimResult(false, true, current, current, best, false); }
    public static ClaimResult failed() { return new ClaimResult(false, false, 0, 0, 0, false); }
}
