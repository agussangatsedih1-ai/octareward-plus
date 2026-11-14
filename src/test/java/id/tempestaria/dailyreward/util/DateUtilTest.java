package id.tempestaria.dailyreward.util;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class DateUtilTest {
    @Test void usesPreviousCalendarDateBeforeConfiguredReset() {
        ZoneId jakarta = ZoneId.of("Asia/Jakarta");
        Instant now = ZonedDateTime.of(2026, 11, 14, 0, 30, 0, 0, jakarta).toInstant();
        assertEquals(LocalDate.of(2026, 11, 13), DateUtil.periodDate(now, jakarta, LocalTime.of(4, 0)));
    }

    @Test void usesCurrentDateAfterConfiguredReset() {
        ZoneId jakarta = ZoneId.of("Asia/Jakarta");
        Instant now = ZonedDateTime.of(2026, 11, 14, 4, 0, 0, 0, jakarta).toInstant();
        assertEquals(LocalDate.of(2026, 11, 14), DateUtil.periodDate(now, jakarta, LocalTime.of(4, 0)));
    }

    @Test void nextResetRollsToTomorrowWhenTodayResetHasPassed() {
        ZoneId jakarta = ZoneId.of("Asia/Jakarta");
        Instant now = ZonedDateTime.of(2026, 11, 14, 5, 0, 0, 0, jakarta).toInstant();
        Instant expected = ZonedDateTime.of(2026, 11, 15, 4, 0, 0, 0, jakarta).toInstant();
        assertEquals(expected, DateUtil.nextReset(now, jakarta, LocalTime.of(4, 0)));
    }

    @Test void streakFollowsResetPauseAndGracePolicies() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        assertEquals(5, DateUtil.nextStreak(4, "2026-11-13", today, "RESET", 1));
        assertEquals(4, DateUtil.nextStreak(4, "2026-11-12", today, "PAUSE", 1));
        assertEquals(5, DateUtil.nextStreak(4, "2026-11-12", today, "GRACE_PERIOD", 1));
        assertEquals(1, DateUtil.nextStreak(4, "2026-11-11", today, "GRACE_PERIOD", 1));
    }
}
