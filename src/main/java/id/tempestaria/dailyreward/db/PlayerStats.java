package id.tempestaria.dailyreward.db;

public record PlayerStats(long playtimeMillis, int currentStreak, int bestStreak, String lastDailyPeriod) {
    public static PlayerStats empty() { return new PlayerStats(0L, 0, 0, ""); }
}
