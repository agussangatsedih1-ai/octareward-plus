package id.tempestaria.dailyreward.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtil {
    private DateUtil() {}

    public static LocalDate periodDate(Instant now, ZoneId zone, LocalTime reset) {
        ZonedDateTime local = now.atZone(zone);
        return local.toLocalTime().isBefore(reset) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    public static Instant nextReset(Instant now, ZoneId zone, LocalTime reset) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime candidate = local.toLocalDate().atTime(reset).atZone(zone);
        if (!candidate.toInstant().isAfter(now)) candidate = candidate.plusDays(1);
        return candidate.toInstant();
    }

    public static int nextStreak(int current, String lastPeriod, LocalDate currentPeriod,
                                 String policy, int graceDays) {
        if (lastPeriod == null || lastPeriod.isBlank()) return 1;
        LocalDate previous;
        try { previous = LocalDate.parse(lastPeriod); }
        catch (RuntimeException ex) { return 1; }
        long gap = ChronoUnit.DAYS.between(previous, currentPeriod);
        if (gap <= 0) return current;
        if (gap == 1) return current + 1;
        return switch (policy.toUpperCase()) {
            case "PAUSE" -> current;
            case "GRACE_PERIOD" -> gap - 1 <= Math.max(0, graceDays) ? current + 1 : 1;
            default -> 1;
        };
    }

    public static String format(Instant instant, ZoneId zone, String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant);
    }
}
