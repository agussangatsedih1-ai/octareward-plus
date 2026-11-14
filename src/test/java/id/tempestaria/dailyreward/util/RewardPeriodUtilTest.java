package id.tempestaria.dailyreward.util;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardPeriodUtilTest {
    private final ZoneId jakarta = ZoneId.of("Asia/Jakarta");

    @Test void dailyKeyUsesConfiguredResetBoundary() {
        Instant beforeReset = ZonedDateTime.of(2026, 11, 14, 0, 15, 0, 0, jakarta).toInstant();
        assertEquals("2026-11-13", RewardPeriodUtil.key(beforeReset, jakarta, LocalTime.of(4, 0), "daily"));
    }

    @Test void weeklyKeyUsesIsoWeekAndMondayBoundary() {
        Instant sunday = ZonedDateTime.of(2026, 1, 4, 12, 0, 0, 0, jakarta).toInstant();
        Instant monday = ZonedDateTime.of(2026, 1, 5, 12, 0, 0, 0, jakarta).toInstant();
        assertEquals("2026-W01", RewardPeriodUtil.key(sunday, jakarta, LocalTime.MIDNIGHT, "weekly"));
        assertEquals("2026-W02", RewardPeriodUtil.key(monday, jakarta, LocalTime.MIDNIGHT, "weekly"));
    }

    @Test void monthlyKeyUsesCalendarMonthAfterDailyReset() {
        Instant beforeReset = ZonedDateTime.of(2026, 12, 1, 0, 10, 0, 0, jakarta).toInstant();
        Instant afterReset = ZonedDateTime.of(2026, 12, 1, 5, 0, 0, 0, jakarta).toInstant();
        assertEquals("2026-11", RewardPeriodUtil.key(beforeReset, jakarta, LocalTime.of(4, 0), "monthly"));
        assertEquals("2026-12", RewardPeriodUtil.key(afterReset, jakarta, LocalTime.of(4, 0), "monthly"));
    }
}
