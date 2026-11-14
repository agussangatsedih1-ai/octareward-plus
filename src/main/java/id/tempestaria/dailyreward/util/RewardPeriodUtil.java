package id.tempestaria.dailyreward.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** Creates stable claim keys for the configured reward reset calendar. */
public final class RewardPeriodUtil {
    private RewardPeriodUtil() {}

    public static String key(Instant now, ZoneId zone, LocalTime dailyReset, String category) {
        LocalDate day = DateUtil.periodDate(now, zone, dailyReset);
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "weekly" -> {
                WeekFields iso = WeekFields.ISO;
                yield String.format(Locale.ROOT, "%04d-W%02d", day.get(iso.weekBasedYear()), day.get(iso.weekOfWeekBasedYear()));
            }
            case "monthly" -> String.format(Locale.ROOT, "%04d-%02d", day.getYear(), day.getMonthValue());
            default -> day.toString();
        };
    }
}
