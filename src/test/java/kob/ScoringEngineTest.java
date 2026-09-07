package kob;

import dto.Game;
import dto.Player;
import dto.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineTest {

    private static final KobConfig CFG = KobConfig.defaults();
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final double EPS = 0.005;

    private static List<BigDecimal> scores(double... values) {
        return IntStream.range(0, values.length)
                .mapToObj(i -> BigDecimal.valueOf(values[i]))
                .sorted(java.util.Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** A result in game {@code gameId} on {@code date}, already worth {@code score}. */
    private static Result resultAt(long gameId, LocalDate date, double score) {
        Game game = new Game(gameId, date, 0, 0);
        Result r = new Result(game, new Player("Test Player"), 1);
        r.setScore(score);
        return r;
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Score range")
    class ScoreRangeTests {

        @Test
        @DisplayName("quartile averages are padded outward by the margin")
        void marginIsApplied() {
            // 8 players, quartile = 2. Top two average 70, bottom two average 30.
            var range = ScoringEngine.computeScoreRange(
                    scores(72, 68, 60, 55, 45, 40, 32, 28), CFG);
            assertEquals(80.0, range.highest(), EPS);  // 70 + 10
            assertEquals(20.0, range.lowest(),  EPS);  // 30 - 10
        }

        @Test
        @DisplayName("the floor is clamped down to include a player below it")
        void floorClampsToWorstPlayer() {
            // Bottom quartile averages 30 -> floor 20, but someone sits at 15.
            var range = ScoringEngine.computeScoreRange(
                    scores(72, 68, 60, 55, 45, 40, 45, 15), CFG);
            assertEquals(15.0, range.lowest(), EPS,
                    "floor must drop to cover the lowest-rated player present");
        }

        @Test
        @DisplayName("the ceiling is clamped up to include a player above it")
        void ceilingClampsToBestPlayer() {
            // Top quartile averages 71 -> ceiling 81, but the leader is at 95.
            var range = ScoringEngine.computeScoreRange(
                    scores(95, 47, 46, 45, 44, 43, 42, 41), CFG);
            assertEquals(95.0, range.highest(), EPS,
                    "ceiling must rise to cover the highest-rated player present");
        }

        @Test
        @DisplayName("ceiling matches the real top-quartile figure from game #447")
        void matchesGame447Ceiling() {
            // The five highest master scores going into game #447 averaged 66.4,
            // which the sheet reported as a ceiling of 76.4.
            List<BigDecimal> field = new ArrayList<>();
            for (double v : new double[]{68.8, 68.6, 68.0, 64.3, 62.2,
                                          56.8, 55.2, 54.8, 54.6, 53.8,
                                          52.5, 52.3, 47.1, 43.8, 42.6,
                                          39.6, 39.0, 36.5, 33.0, 25.5}) {
                field.add(BigDecimal.valueOf(v));
            }
            field.sort(java.util.Comparator.reverseOrder());

            var range = ScoringEngine.computeScoreRange(field, CFG);
            assertEquals(76.38, range.highest(), 0.01,
                    "ceiling = mean of the top 5 (66.38) + margin");
        }

        @Test
        @DisplayName("edge case: a field below the quartile size collapses the floor to zero")
        void tinyFieldCollapsesFloor() {
            // With 2 players the quartile sample is empty, so both ends start at 0.
            // The ceiling is then clamped up to the best player, but the floor is
            // only clamped *down*, so it stays at 0 rather than rising to 40.
            // Unreachable in practice: the attendance gate rejects fields this small.
            var range = ScoringEngine.computeScoreRange(scores(60, 40), CFG);
            assertEquals(60.0, range.highest(), EPS, "ceiling still clamps up to the best player");
            assertEquals(0.0,  range.lowest(),  EPS, "documents current behaviour, not desired behaviour");
        }

        @Test
        void emptyFieldIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> ScoringEngine.computeScoreRange(List.of(), CFG));
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Game score")
    class GameScoreTests {

        private final ScoringEngine.ScoreRange g447 = new ScoringEngine.ScoreRange(76.4, 25.5);

        @Test
        @DisplayName("first place takes the ceiling, last place the floor")
        void endpoints() {
            assertEquals(76.4, ScoringEngine.gameScore(1,  g447, 20), EPS);
            assertEquals(25.5, ScoringEngine.gameScore(20, g447, 20), EPS);
        }

        @Test
        @DisplayName("positions are evenly spaced across the range")
        void evenlySpaced() {
            double step = (76.4 - 25.5) / 19;
            assertEquals(76.4 - step,     ScoringEngine.gameScore(2, g447, 20), EPS);
            assertEquals(76.4 - 2 * step, ScoringEngine.gameScore(3, g447, 20), EPS);
        }

        @Test
        @DisplayName("a tie at 1.5 scores exactly between first and second")
        void tieSplitsTheDifference() {
            double first  = ScoringEngine.gameScore(1,   g447, 20);
            double second = ScoringEngine.gameScore(2,   g447, 20);
            double tied   = ScoringEngine.gameScore(1.5, g447, 20);
            assertEquals((first + second) / 2, tied, EPS);
        }

        @Test
        @DisplayName("matches the scores recorded for game #447")
        void matchesGame447Scores() {
            assertEquals(75.1, ScoringEngine.gameScore(1.5, g447, 20), 0.05);
            assertEquals(71.0, ScoringEngine.gameScore(3,   g447, 20), 0.05);
            assertEquals(65.7, ScoringEngine.gameScore(5,   g447, 20), 0.05);
        }

        @Test
        @DisplayName("a solo entrant takes the ceiling rather than dividing by zero")
        void singlePlayer() {
            double score = ScoringEngine.gameScore(1, g447, 1);
            assertTrue(Double.isFinite(score));
            assertEquals(76.4, score, EPS);
        }
    }

    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Master score bucketing")
    class MasterScoreTests {

        @Test
        @DisplayName("no history returns the initial score")
        void emptyHistory() {
            assertEquals(CFG.initialScore,
                    ScoringEngine.masterScore(List.of(), 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("results inside the recent window each count individually")
        void recentCountIndividually() {
            List<Result> history = List.of(
                    resultAt(98, TODAY.minusDays(7),  60),
                    resultAt(99, TODAY.minusDays(4),  70),
                    resultAt(100, TODAY,              80));
            // gaps 2, 1, 0 -> all recent -> plain mean
            assertEquals(70.0, ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("the mid bucket collapses to a single averaged contribution")
        void midBucketCollapses() {
            // One recent result at 80; three mid results (gaps 9..11) averaging 50.
            List<Result> history = List.of(
                    resultAt(100, TODAY,             80),
                    resultAt(91,  TODAY.minusDays(30), 40),
                    resultAt(90,  TODAY.minusDays(33), 50),
                    resultAt(89,  TODAY.minusDays(36), 60));
            // contributions: 80 (recent) + 50 (mid mean) over n=2
            assertEquals(65.0, ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("the old bucket collapses to a single averaged contribution")
        void oldBucketCollapses() {
            List<Result> history = List.of(
                    resultAt(100, TODAY,              90),
                    resultAt(80,  TODAY.minusDays(60), 30),
                    resultAt(79,  TODAY.minusDays(63), 50));
            // gaps 0 (recent), 20 and 21 (old, mean 40) -> (90 + 40) / 2
            assertEquals(65.0, ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("gap 8 is recent and gap 16 is old — the boundaries hold")
        void bucketBoundaries() {
            // A result at exactly gap 8 must count individually, not be averaged.
            List<Result> atEight = List.of(
                    resultAt(100, TODAY, 80),
                    resultAt(92,  TODAY.minusDays(28), 40));
            assertEquals(60.0, ScoringEngine.masterScore(atEight, 100, TODAY, CFG), EPS,
                    "gap 8 belongs to the recent bucket");

            // Two results at gap 16 and 17 must both land in old and average together.
            List<Result> atSixteen = List.of(
                    resultAt(100, TODAY, 80),
                    resultAt(84,  TODAY.minusDays(50), 30),
                    resultAt(83,  TODAY.minusDays(53), 50));
            assertEquals(60.0, ScoringEngine.masterScore(atSixteen, 100, TODAY, CFG), EPS,
                    "gap 16 belongs to the old bucket, so both collapse to one value of 40");
        }

        @Test
        @DisplayName("results older than a year are excluded")
        void oneYearCutoff() {
            List<Result> history = List.of(
                    resultAt(100, TODAY,                    80),
                    resultAt(50,  TODAY.minusYears(1).minusDays(1), 20));
            assertEquals(80.0, ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS,
                    "the stale result must not pull the score down");
        }

        @Test
        @DisplayName("the cutoff follows the reference date, not the last game")
        void cutoffMovesWithReferenceDate() {
            Result recent = resultAt(100, LocalDate.of(2025, 3, 29), 70);
            List<Result> history = List.of(recent);

            // Scored the day after: still inside the window.
            assertEquals(70.0,
                    ScoringEngine.masterScore(history, 100, LocalDate.of(2025, 3, 30), CFG), EPS);

            // Scored a year later: aged out, so the player falls back to the initial score.
            assertEquals(CFG.initialScore,
                    ScoringEngine.masterScore(history, 100, LocalDate.of(2026, 4, 5), CFG), EPS);
        }

        @Test
        @DisplayName("a player whose results have all aged out no longer qualifies")
        void agedOutPlayerDoesNotQualify() {
            List<Result> history = List.of(resultAt(50, TODAY.minusYears(2), 70));
            assertFalse(ScoringEngine.hasQualifyingResults(history, 100, TODAY, CFG));
            assertEquals(CFG.initialScore,
                    ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("zero-scored results are ignored")
        void zeroScoresIgnored() {
            List<Result> history = List.of(
                    resultAt(100, TODAY, 80),
                    resultAt(99,  TODAY.minusDays(3), 0));
            assertEquals(80.0, ScoringEngine.masterScore(history, 100, TODAY, CFG), EPS);
        }

        @Test
        @DisplayName("disabling the one-year limit keeps old results")
        void limitCanBeDisabled() {
            KobConfig noLimit = new KobConfig(50, 8, 10, 8, 4, false);
            List<Result> history = List.of(
                    resultAt(100, TODAY,               80),
                    resultAt(99,  TODAY.minusYears(5), 40));
            assertEquals(60.0, ScoringEngine.masterScore(history, 100, TODAY, noLimit), EPS);
        }

        @Test
        @DisplayName("a wider recent window pulls more results into individual counting")
        void windowSizeIsHonoured() {
            List<Result> history = List.of(
                    resultAt(100, TODAY,               90),
                    resultAt(88,  TODAY.minusDays(40), 30));
            // Default window 8: gap 12 is mid -> (90 + 30) / 2 = 60 (same value, different path)
            // Window 20: gap 12 becomes recent -> still (90 + 30) / 2, so use three results
            KobConfig wide = new KobConfig(50, 8, 10, 20, 4, true);
            List<Result> three = List.of(
                    resultAt(100, TODAY,               90),
                    resultAt(89,  TODAY.minusDays(38), 30),
                    resultAt(88,  TODAY.minusDays(40), 30));
            // Under the wide window all three are recent: (90+30+30)/3 = 50
            assertEquals(50.0, ScoringEngine.masterScore(three, 100, TODAY, wide), EPS);
            // Under the default window the two old ones collapse to one: (90+30)/2 = 60
            assertEquals(60.0, ScoringEngine.masterScore(three, 100, TODAY, CFG), EPS);
        }
    }
}
