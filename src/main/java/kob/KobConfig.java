package kob;

import java.util.Properties;

/**
 * Immutable holder for every tunable scoring parameter.
 *
 * Values are read from {@code config.properties} at startup so the engine can be
 * retuned without a recompile. Any key that is absent or malformed falls back to
 * the historical default, so an empty properties file reproduces the behaviour
 * the system shipped with.
 */
public final class KobConfig {

    /** Master score every player starts at, and returns to when they age out. */
    public final int initialScore;
    /** Attendance gate: games with fewer participants are ignored entirely. */
    public final int minimumNbPlayers;
    /** Padding above/below the field so the top and bottom players can still move. */
    public final int scoreRangeMargin;
    /** Bucket width in games: recent <= W, mid W+1..2W-1, old >= 2W. */
    public final int recentWindowSize;
    /** Divisor picking the top/bottom sample of a field (4 -> quartiles). */
    public final int quartileDivisor;
    /** Whether results older than a year are discarded. */
    public final boolean limitToAYear;

    public KobConfig(int initialScore, int minimumNbPlayers, int scoreRangeMargin,
                     int recentWindowSize, int quartileDivisor, boolean limitToAYear) {
        if (recentWindowSize < 1)  throw new IllegalArgumentException("recentWindowSize must be >= 1");
        if (quartileDivisor < 1)   throw new IllegalArgumentException("quartileDivisor must be >= 1");
        if (minimumNbPlayers < 1)  throw new IllegalArgumentException("minimumNbPlayers must be >= 1");
        this.initialScore     = initialScore;
        this.minimumNbPlayers = minimumNbPlayers;
        this.scoreRangeMargin = scoreRangeMargin;
        this.recentWindowSize = recentWindowSize;
        this.quartileDivisor  = quartileDivisor;
        this.limitToAYear     = limitToAYear;
    }

    /** The values the engine used before any of this was configurable. */
    public static KobConfig defaults() {
        return new KobConfig(50, 8, 10, 8, 4, true);
    }

    /**
     * Reads the {@code scoring.*} keys, falling back to {@link #defaults()} per key.
     * A malformed value is reported and the default is used rather than failing the run.
     */
    public static KobConfig fromProperties(Properties p) {
        KobConfig d = defaults();
        return new KobConfig(
                readInt(p,  "scoring.initial.score",    d.initialScore),
                readInt(p,  "scoring.minimum.players",  d.minimumNbPlayers),
                readInt(p,  "scoring.range.margin",     d.scoreRangeMargin),
                readInt(p,  "scoring.recent.window",    d.recentWindowSize),
                readInt(p,  "scoring.quartile.divisor", d.quartileDivisor),
                readBool(p, "scoring.limit.to.year",    d.limitToAYear));
    }

    private static int readInt(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.err.printf("config: '%s' is not a number ('%s') — using %d%n", key, raw, fallback);
            return fallback;
        }
    }

    private static boolean readBool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    @Override
    public String toString() {
        return String.format(
                "KobConfig[initial=%d, minPlayers=%d, margin=%d, window=%d, quartile=%d, limitToYear=%s]",
                initialScore, minimumNbPlayers, scoreRangeMargin, recentWindowSize, quartileDivisor, limitToAYear);
    }
}
