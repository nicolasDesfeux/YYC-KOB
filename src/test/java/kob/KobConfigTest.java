package kob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KobConfig")
class KobConfigTest {

    @Test
    @DisplayName("defaults reproduce the values the engine shipped with")
    void defaultsAreTheHistoricalValues() {
        KobConfig d = KobConfig.defaults();
        assertEquals(50, d.initialScore);
        assertEquals(8,  d.minimumNbPlayers);
        assertEquals(10, d.scoreRangeMargin);
        assertEquals(8,  d.recentWindowSize);
        assertEquals(4,  d.quartileDivisor);
        assertTrue(d.limitToAYear);
    }

    @Test
    @DisplayName("an empty properties file yields the defaults")
    void emptyPropertiesFallsBack() {
        KobConfig c = KobConfig.fromProperties(new Properties());
        KobConfig d = KobConfig.defaults();
        assertEquals(d.initialScore,     c.initialScore);
        assertEquals(d.recentWindowSize, c.recentWindowSize);
        assertEquals(d.limitToAYear,     c.limitToAYear);
    }

    @Test
    @DisplayName("values are read from the scoring.* keys")
    void readsOverrides() {
        Properties p = new Properties();
        p.setProperty("scoring.initial.score",    "40");
        p.setProperty("scoring.minimum.players",  "6");
        p.setProperty("scoring.range.margin",     "15");
        p.setProperty("scoring.recent.window",    "12");
        p.setProperty("scoring.quartile.divisor", "3");
        p.setProperty("scoring.limit.to.year",    "false");

        KobConfig c = KobConfig.fromProperties(p);
        assertEquals(40, c.initialScore);
        assertEquals(6,  c.minimumNbPlayers);
        assertEquals(15, c.scoreRangeMargin);
        assertEquals(12, c.recentWindowSize);
        assertEquals(3,  c.quartileDivisor);
        assertFalse(c.limitToAYear);
    }

    @Test
    @DisplayName("a malformed number falls back instead of failing the run")
    void malformedValueFallsBack() {
        Properties p = new Properties();
        p.setProperty("scoring.recent.window", "not-a-number");
        assertEquals(KobConfig.defaults().recentWindowSize,
                KobConfig.fromProperties(p).recentWindowSize);
    }

    @Test
    @DisplayName("whitespace around a value is tolerated")
    void trimsValues() {
        Properties p = new Properties();
        p.setProperty("scoring.recent.window", "  10  ");
        assertEquals(10, KobConfig.fromProperties(p).recentWindowSize);
    }

    @Test
    @DisplayName("nonsensical window sizes are rejected outright")
    void rejectsInvalidWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> new KobConfig(50, 8, 10, 0, 4, true));
        assertThrows(IllegalArgumentException.class,
                () -> new KobConfig(50, 8, 10, 8, 0, true));
    }
}
