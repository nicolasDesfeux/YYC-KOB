package kob;

import dto.Result;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * The scoring maths, as pure functions.
 *
 * Nothing here touches I/O or mutable application state, so every rule can be
 * exercised directly in tests. {@link KOB} owns the forward pass over history
 * and delegates the arithmetic to this class.
 */
public final class ScoringEngine {

    private ScoringEngine() {}

    /** The ceiling and floor of points available in a single game. */
    public record ScoreRange(double highest, double lowest) {
        public double spread() { return highest - lowest; }
    }

    /**
     * Derives the points available in a game from the master scores of who turned up.
     *
     * The top and bottom quartiles set the initial ceiling and floor, each pushed out
     * by {@code scoreRangeMargin} so the best and worst player present can still move.
     * Both ends are then clamped outward if any participant already sits beyond them,
     * guaranteeing the range spans the whole field.
     *
     * @param preGameScoresDescending participants' master scores before the game, highest first
     */
    public static ScoreRange computeScoreRange(List<BigDecimal> preGameScoresDescending, KobConfig cfg) {
        if (preGameScoresDescending.isEmpty())
            throw new IllegalArgumentException("cannot compute a score range for an empty field");

        int n = preGameScoresDescending.size();
        int sizeForMargin = n / cfg.quartileDivisor;

        OptionalDouble topAvg = preGameScoresDescending.subList(0, sizeForMargin).stream()
                .mapToDouble(BigDecimal::doubleValue).average();
        double highest = topAvg.isPresent() ? topAvg.getAsDouble() + cfg.scoreRangeMargin : 0;
        double bestPlayer = preGameScoresDescending.get(0).doubleValue();
        if (highest < bestPlayer) highest = bestPlayer;

        OptionalDouble botAvg = preGameScoresDescending.subList(n - sizeForMargin, n).stream()
                .mapToDouble(BigDecimal::doubleValue).average();
        double lowest = botAvg.isPresent() ? botAvg.getAsDouble() - cfg.scoreRangeMargin : 0;
        double worstPlayer = preGameScoresDescending.get(n - 1).doubleValue();
        if (lowest > worstPlayer) lowest = worstPlayer;

        return new ScoreRange(highest, lowest);
    }

    /**
     * Maps a finish position onto the game's range. First place takes the ceiling,
     * last place the floor, everyone else an even step between.
     *
     * Ties are recorded as fractional positions (two players tied for first are both
     * {@code 1.5}), which lands them exactly on the average of the places they shared.
     */
    public static double gameScore(double finish, ScoreRange range, int numPlayers) {
        if (numPlayers < 2) return range.highest();
        return range.highest() - ((finish - 1) * range.spread() / (numPlayers - 1));
    }

    /**
     * Recency-weighted average of a player's results, as of {@code currentGameId}.
     *
     * Results within {@code recentWindowSize} games each count individually; the next
     * window collapses to a single averaged value, and everything older collapses to
     * one more. Recent form therefore dominates while history still anchors the score.
     *
     * @return the new master score, or {@code initialScore} if nothing qualifies
     */
    public static double masterScore(List<Result> history, long currentGameId,
                                     LocalDate referenceDate, KobConfig cfg) {
        if (history.isEmpty()) return cfg.initialScore;

        List<Result> filtered = cfg.limitToAYear
                ? history.stream()
                    .filter(r -> r.getSession().getDate().isAfter(referenceDate.minusYears(1)))
                    .collect(Collectors.toList())
                : history;

        int w = cfg.recentWindowSize;

        List<Result> recent = filtered.stream()
                .filter(r -> r.getScore() != 0 && gap(currentGameId, r) <= w)
                .collect(Collectors.toList());

        OptionalDouble mid = filtered.stream()
                .filter(r -> r.getScore() != 0 && gap(currentGameId, r) > w && gap(currentGameId, r) < w * 2L)
                .mapToDouble(Result::getScore).average();

        OptionalDouble old = filtered.stream()
                .filter(r -> r.getScore() != 0 && gap(currentGameId, r) >= w * 2L)
                .mapToDouble(Result::getScore).average();

        long contributions = recent.size() + (mid.isPresent() ? 1 : 0) + (old.isPresent() ? 1 : 0);
        if (contributions == 0) return cfg.initialScore;

        double total = recent.stream().mapToDouble(Result::getScore).sum()
                + mid.orElse(0)
                + old.orElse(0);
        return total / contributions;
    }

    /** True when the player has at least one result inside the scoring window. */
    public static boolean hasQualifyingResults(List<Result> history, long currentGameId,
                                               LocalDate referenceDate, KobConfig cfg) {
        if (history.isEmpty()) return false;
        List<Result> filtered = cfg.limitToAYear
                ? history.stream()
                    .filter(r -> r.getSession().getDate().isAfter(referenceDate.minusYears(1)))
                    .collect(Collectors.toList())
                : history;
        return filtered.stream().anyMatch(r -> r.getScore() != 0);
    }

    /** Distance in game IDs between the game being scored and an earlier result. */
    private static long gap(long currentGameId, Result r) {
        return currentGameId - r.getSession().getId();
    }

    /** Sorts a field by master score, highest first — the order the range calculation expects. */
    public static List<BigDecimal> descendingScores(List<dto.Player> players) {
        return players.stream()
                .map(dto.Player::getMasterScore)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }
}
