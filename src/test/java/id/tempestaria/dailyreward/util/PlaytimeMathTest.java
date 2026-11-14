package id.tempestaria.dailyreward.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaytimeMathTest {
    @Test void convertsTicksToMinutes() {
        assertEquals(1, PlaytimeMath.ticksToMinutes(1_200));
        assertEquals(60, PlaytimeMath.ticksToMinutes(72_000));
        assertEquals(0, PlaytimeMath.ticksToMinutes(1_199));
    }
    @Test void convertsTicksToMillis() {
        assertEquals(50, PlaytimeMath.ticksToMillis(1));
        assertEquals(3_600_000, PlaytimeMath.ticksToMillis(72_000));
        assertEquals(0, PlaytimeMath.ticksToMillis(-1));
    }
}
