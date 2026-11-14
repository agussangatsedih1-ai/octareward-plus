package id.tempestaria.dailyreward.util;

public final class PlaytimeMath {
    private PlaytimeMath() {}
    /** Bukkit Statistic.PLAY_ONE_MINUTE is misleadingly named; Paper documents that it stores ticks played (20 ticks = 1 second). */
    public static long ticksToMinutes(long ticks) { return Math.max(0L, ticks) / 1_200L; }
    public static long ticksToMillis(long ticks) { return Math.max(0L, ticks) * 50L; }
}
