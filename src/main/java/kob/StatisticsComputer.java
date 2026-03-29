package kob;

import dto.Game;
import dto.GlobalStats;
import dto.Player;
import dto.PlayerStats;
import dto.Result;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes global and per-player statistics from game results.
 *
 * Must be called AFTER computeScoreEvolution() so that
 * Result.playerMasterScoreBeforeGame is populated for every result.
 *
 * Tier definitions:
 *   numTiers       = max(1, numPlayersInGame / 4)   (last tier absorbs the remainder)
 *   startingTier   = min((masterScoreRank - 1) / 4 + 1, numTiers)   — used for moves/teammates only
 *   finishingTier  = min((finishingPosition - 1) / 4 + 1, numTiers) — used for games/wins/participation
 *   won            = (finishingPosition - 1) % 4 == 0  →  positions 1, 5, 9, …
 */
public class StatisticsComputer {

    private final List<Game> allGames;
    private final Map<Game, List<Result>> resultsByGame;

    public StatisticsComputer(List<Game> allGames, Map<Game, List<Result>> resultsByGame) {
        this.allGames = allGames;
        this.resultsByGame = resultsByGame;
    }

    public static class ComputedStats {
        public final GlobalStats global;
        public final List<PlayerStats> players;

        ComputedStats(GlobalStats global, List<PlayerStats> players) {
            this.global = global;
            this.players = players;
        }
    }

    public ComputedStats compute() {
        Map<Player, Integer> gamesPlayed   = new HashMap<>();
        Map<Player, Map<Integer, Integer>> gamesByTier  = new HashMap<>(); // player -> finishingTier -> count
        Map<Player, Integer> totalWins     = new HashMap<>();
        Map<Player, Map<Integer, Integer>> winsByTier   = new HashMap<>(); // player -> finishingTier -> count
        Map<Player, Integer> movesUp       = new HashMap<>();
        Map<Player, Integer> movesDown     = new HashMap<>();
        Map<Player, Integer> stays         = new HashMap<>();
        Map<Player, Map<Player, Integer>> goodTeammates = new HashMap<>(); // player -> teammate -> count
        Map<Player, Map<Player, Integer>> badTeammates  = new HashMap<>();
        Map<Player, Integer> firstGameIndex = new HashMap<>();

        int maxTier = 1;

        for (int gameIdx = 0; gameIdx < allGames.size(); gameIdx++) {
            Game game = allGames.get(gameIdx);
            List<Result> results = resultsByGame.getOrDefault(game, Collections.emptyList());
            if (results.isEmpty()) continue;

            int numPlayers = results.size();
            int numTiers = Math.max(1, numPlayers / 4);
            maxTier = Math.max(maxTier, numTiers);

            // Assign starting tiers: sort by master score before game descending
            List<Result> byScore = results.stream()
                    .filter(r -> r.getPlayerMasterScoreBeforeGame() != null)
                    .sorted((a, b) -> b.getPlayerMasterScoreBeforeGame().compareTo(a.getPlayerMasterScoreBeforeGame()))
                    .collect(Collectors.toList());

            Map<Player, Integer> startingTierMap = new HashMap<>();
            for (int i = 0; i < byScore.size(); i++) {
                int tier = Math.min(i / 4 + 1, numTiers);
                startingTierMap.put(byScore.get(i).getPlayer(), tier);
            }

            // Group players by starting tier for teammate lookups
            Map<Integer, List<Player>> tierGroups = new HashMap<>();
            startingTierMap.forEach((p, t) ->
                    tierGroups.computeIfAbsent(t, k -> new ArrayList<>()).add(p));

            for (Result result : results) {
                Player player = result.getPlayer();
                int finishingRank = (int) result.getResult();
                int finishingTier = Math.min((finishingRank - 1) / 4 + 1, numTiers);
                int startingTier  = startingTierMap.getOrDefault(player, finishingTier);
                boolean won = (finishingRank - 1) % 4 == 0;
                int delta = Integer.compare(finishingTier, startingTier); // -1=UP, 0=SAME, +1=DOWN

                firstGameIndex.putIfAbsent(player, gameIdx);
                gamesPlayed.merge(player, 1, Integer::sum);
                gamesByTier.computeIfAbsent(player, k -> new HashMap<>()).merge(finishingTier, 1, Integer::sum);

                if (won) {
                    totalWins.merge(player, 1, Integer::sum);
                    winsByTier.computeIfAbsent(player, k -> new HashMap<>()).merge(finishingTier, 1, Integer::sum);
                }
                if (!result.isDebutGame()) {
                    if      (delta < 0) movesUp.merge(player, 1, Integer::sum);
                    else if (delta > 0) movesDown.merge(player, 1, Integer::sum);
                    else                stays.merge(player, 1, Integer::sum);
                }

                boolean goodGame = won || delta < 0;
                boolean badGame  = delta > 0;
                for (Player mate : tierGroups.getOrDefault(startingTier, Collections.emptyList())) {
                    if (mate.equals(player)) continue;
                    if (goodGame) goodTeammates.computeIfAbsent(player, k -> new HashMap<>()).merge(mate, 1, Integer::sum);
                    if (badGame)  badTeammates.computeIfAbsent(player, k -> new HashMap<>()).merge(mate, 1, Integer::sum);
                }
            }
        }

        // Build per-player stats
        List<PlayerStats> playerStatsList = new ArrayList<>();
        for (Player player : gamesPlayed.keySet()) {
            int played = gamesPlayed.get(player);
            int firstIdx = firstGameIndex.getOrDefault(player, 0);
            int available = allGames.size() - firstIdx;
            double rate = available > 0 ? (double) played / available : 0.0;

            int mostPlayedTier = gamesByTier.getOrDefault(player, Collections.emptyMap())
                    .entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(1);

            Map<Integer, Integer> gamesTierMap = gamesByTier.getOrDefault(player, Collections.emptyMap());
            playerStatsList.add(new PlayerStats(
                    player, played, rate,
                    totalWins.getOrDefault(player, 0),
                    winsByTier.getOrDefault(player, Collections.emptyMap()),
                    gamesTierMap,
                    movesUp.getOrDefault(player, 0),
                    movesDown.getOrDefault(player, 0),
                    mostPlayedTier,
                    argmax(goodTeammates.get(player)),
                    argmax(badTeammates.get(player))
            ));
        }
        playerStatsList.sort((a, b) -> b.gamesPlayed - a.gamesPlayed);

        GlobalStats global = buildGlobalStats(maxTier, gamesPlayed, gamesByTier,
                totalWins, winsByTier, movesUp, movesDown, stays);

        return new ComputedStats(global, playerStatsList);
    }

    private GlobalStats buildGlobalStats(int maxTier,
                                         Map<Player, Integer> gamesPlayed,
                                         Map<Player, Map<Integer, Integer>> gamesByTier,
                                         Map<Player, Integer> totalWins,
                                         Map<Player, Map<Integer, Integer>> winsByTier,
                                         Map<Player, Integer> movesUp,
                                         Map<Player, Integer> movesDown,
                                         Map<Player, Integer> stays) {
        Player topGamesPlayer = argmax(gamesPlayed);

        Map<Integer, Player> mostGamesPerTier = new HashMap<>();
        Map<Integer, Integer> mostGamesPerTierCount = new HashMap<>();
        for (int t = 1; t <= maxTier; t++) {
            Map<Player, Integer> counts = countForTier(gamesByTier, t);
            Player best = argmax(counts);
            mostGamesPerTier.put(t, best);
            mostGamesPerTierCount.put(t, best != null ? counts.get(best) : 0);
        }

        Player topWinsPlayer = argmax(totalWins);
        double topWinsRate = rate(totalWins, gamesPlayed, topWinsPlayer);

        Map<Integer, Player> mostWinsPerTier = new HashMap<>();
        Map<Integer, Integer> mostWinsPerTierCount = new HashMap<>();
        Map<Integer, Double> mostWinsPerTierRate = new HashMap<>();
        for (int t = 1; t <= maxTier; t++) {
            Map<Player, Integer> winCounts  = countForTier(winsByTier, t);
            Map<Player, Integer> gameCounts = countForTier(gamesByTier, t);
            Player best = argmax(winCounts);
            mostWinsPerTier.put(t, best);
            mostWinsPerTierCount.put(t, best != null ? winCounts.get(best) : 0);
            mostWinsPerTierRate.put(t, best != null ? rate(winCounts, gameCounts, best) : 0.0);
        }

        Player topMovedUp    = argmax(movesUp);
        Player topMovedDown  = argmax(movesDown);
        Player topConsistent = argmax(stays);

        return new GlobalStats(maxTier,
                topGamesPlayer,  topGamesPlayer  != null ? gamesPlayed.get(topGamesPlayer)  : 0,
                mostGamesPerTier, mostGamesPerTierCount,
                topWinsPlayer,   topWinsPlayer   != null ? totalWins.get(topWinsPlayer)   : 0, topWinsRate,
                mostWinsPerTier, mostWinsPerTierCount, mostWinsPerTierRate,
                topMovedUp,      topMovedUp      != null ? movesUp.get(topMovedUp)        : 0, rate(movesUp,   gamesPlayed, topMovedUp),
                topMovedDown,    topMovedDown    != null ? movesDown.get(topMovedDown)     : 0, rate(movesDown, gamesPlayed, topMovedDown),
                topConsistent,   topConsistent   != null ? stays.get(topConsistent)        : 0, rate(stays,     gamesPlayed, topConsistent));
    }

    /** Returns a map of player -> count for a specific tier, across all players. */
    private static Map<Player, Integer> countForTier(Map<Player, Map<Integer, Integer>> nested, int tier) {
        Map<Player, Integer> out = new HashMap<>();
        for (Map.Entry<Player, Map<Integer, Integer>> e : nested.entrySet()) {
            int c = e.getValue().getOrDefault(tier, 0);
            if (c > 0) out.put(e.getKey(), c);
        }
        return out;
    }

    /** Returns the key with the highest value, or null if the map is null/empty. */
    private static Player argmax(Map<Player, Integer> map) {
        if (map == null || map.isEmpty()) return null;
        return map.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Returns numerator[player] / denominator[player], or 0 if player is null or denominator is 0. */
    private static double rate(Map<Player, Integer> numerator, Map<Player, Integer> denominator, Player player) {
        if (player == null) return 0.0;
        int num = numerator.getOrDefault(player, 0);
        int den = denominator.getOrDefault(player, 0);
        return den > 0 ? (double) num / den : 0.0;
    }
}
