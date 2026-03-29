package kob;

import dao.DaoFactory;
import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.RankingWriter;
import dao.daoInterface.ResultDao;
import dao.daoInterface.ScoreCacheDao;
import dto.Game;
import dto.Player;
import dto.Result;
import kob.StatisticsComputer.ComputedStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the main application class. It queries and utilizes the different
 * objects from the model to display rankings statistics etc
 */
public class KOB {

    private static final Logger log = LogManager.getLogger(KOB.class);

    // TODO Move those settings to properties database.
    public final static int INITIAL_SCORE = 50;
    public static final int MINIMUM_NB_PLAYERS = 8;
    public static final int SCORE_RANGE_MARGIN = 10; // Ensures top/bottom available scores exceed the highest/lowest player score on the day
    public static final int RECENT_WINDOW_SIZE = 8;  // Number of games per scoring bucket (2 games/week × 4 weeks)
    public static final int QUARTILE_DIVISOR = 4;    // Divisor for top/bottom 25% calculation
    private final ResultDao resultDao;
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final RankingWriter rankingWriter;
    private final ScoreCacheDao scoreCacheDao;
    private String htmlOutputPath;
    public final static DecimalFormat DF = new DecimalFormat("0.0");
    public final static boolean LIMIT_TO_A_YEAR = true;

    private static volatile KOB singleInstance = null;
    private final Map<Player, List<String>> masterScoresEvolution = new HashMap<>();

    /**
     * Starts the system, and sets up data access. It also initializes the data if
     * need be.
     */
    public KOB() {
        Properties properties = new Properties();
        String daoType = "";
        try (InputStream input = KOB.class.getResourceAsStream("/config.properties")) {
            log.debug("Loading properties files");

            if (input == null) {
                log.error("config.properties not found on classpath");
                throw new IllegalStateException("Missing config.properties");
            }
            properties.load(input);

            daoType = properties.getProperty("dao.type");
            htmlOutputPath = properties.getProperty("html.output.path", "dashboard.html");

        } catch (IOException e) {
            log.error("Could not load config.properties, cannot continue", e);
            throw new IllegalStateException("Failed to load configuration", e);
        }
        log.debug("Properties loaded");
        if (!daoType.equals("GSheet") && !daoType.equals("JDBC")) {
            log.error("Unknown dao.type '{}' in config.properties, defaulting to JDBC", daoType);
        }

        DaoFactory factory = DaoFactory.forType(daoType, properties);
        this.gameDao       = factory.createGameDao();
        this.playerDao     = factory.createPlayerDao();
        this.resultDao     = factory.createResultDao(this.gameDao, this.playerDao);
        this.rankingWriter = factory.createRankingWriter();
        this.scoreCacheDao = factory.createScoreCacheDao();
        this.initialize();
    }

    // static method to create instance of Singleton class
    public static synchronized KOB getInstance() {
        if (singleInstance == null)
            singleInstance = new KOB();
        return singleInstance;
    }

    public static void main(String[] args) {
        KOB kob = getInstance();
        if (args.length > 0 && args[0].equals("--migrate-sheet8")) {
            kob.migrateSheet8ToComputedScores();
        } else if (args.length > 0 && args[0].equals("--clear-cache")) {
            kob.clearScoreCache();
        } else if (args.length > 1 && args[0].equals("--as-of")) {
            kob.persistRanking(java.time.LocalDate.parse(args[1]));
        } else {
            kob.persistRanking(null);
        }
    }

    void persistRanking(java.time.LocalDate asOfDate) {
        List<Game> allGames = gameDao.getAllGames();
        allGames.sort(Comparator.comparing(Game::getId));
        if (asOfDate != null) {
            allGames = allGames.stream()
                    .filter(g -> !g.getDate().isAfter(asOfDate))
                    .collect(Collectors.toList());
            log.info("As-of mode: computing ranking as of {} ({} games)", asOfDate, allGames.size());
        }

        if (allGames.isEmpty()) return;

        // Pre-load all results once — O(N) fetches instead of O(N²)
        Map<Game, List<Result>> resultsByGame = new LinkedHashMap<>();
        for (Game game : allGames) {
            List<Result> results = resultDao.getAllResultsFromGame(game);
            if (results != null) resultsByGame.put(game, results);
        }

        // Build per-player result index for the activity filter
        Map<Player, List<Result>> resultsByPlayer = new HashMap<>();
        for (List<Result> gameResults : resultsByGame.values()) {
            for (Result r : gameResults) {
                resultsByPlayer.computeIfAbsent(r.getPlayer(), k -> new ArrayList<>()).add(r);
            }
        }

        Map<Long, Map<String, Double>> cachedScores = scoreCacheDao != null ? scoreCacheDao.load() : Collections.emptyMap();
        if (asOfDate != null && !cachedScores.isEmpty()) {
            // Drop cache entries for games that fall outside the as-of window
            Set<Long> validGameIds = allGames.stream().map(Game::getId).collect(Collectors.toSet());
            cachedScores = cachedScores.entrySet().stream()
                    .filter(e -> validGameIds.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (a, b) -> a, LinkedHashMap::new));
        }
        Map<Long, Map<String, Double>> computedScores = new LinkedHashMap<>();
        List<Player> finalRanking = computeScoreEvolution(allGames, resultsByGame, cachedScores, computedScores);
        if (scoreCacheDao != null && asOfDate == null) scoreCacheDao.save(computedScores);

        Game lastGame = gameDao.getLastCompletedGame();
        List<Player> ranking = finalRanking.stream()
                .filter(player -> {
                    List<Result> results = resultsByPlayer.getOrDefault(player, Collections.emptyList());
                    return results.stream()
                            .anyMatch(r -> r.getSession().getDate().isAfter(lastGame.getDate().minusYears(1)));
                })
                .collect(Collectors.toList());

        rankingWriter.writeRanking(ranking, masterScoresEvolution);
        if (ranking.size() > 1) printTopPlayerBucketDebug(ranking.get(2), allGames, resultsByGame);

        ComputedStats stats = new StatisticsComputer(allGames, resultsByGame).compute();
        rankingWriter.writeStatistics(stats.global, stats.players);

        try {
            new dao.HtmlWriter().write(htmlOutputPath, ranking, stats.global, stats.players, allGames, resultsByGame);
            log.info("HTML dashboard written to {}", htmlOutputPath);
        } catch (IOException e) {
            log.error("Failed to write HTML dashboard", e);
        }
    }

    public ResultDao getResultDao() {
        return resultDao;
    }

    public PlayerDao getPlayerDao() {
        return playerDao;
    }

    public GameDao getGameDao() {
        return gameDao;
    }

    private void printTopPlayerBucketDebug(Player player, List<Game> allGames,
                                            Map<Game, List<Result>> resultsByGame) {
        // Collect all results for this player across all games (scores already set by forward pass)
        List<Result> allPlayerResults = allGames.stream()
                .map(g -> resultsByGame.getOrDefault(g, Collections.emptyList()))
                .flatMap(List::stream)
                .filter(r -> r.getPlayer().equals(player))
                .collect(Collectors.toList());

        // Last 5 games overall (regardless of whether the player participated)
        List<Game> last5 = allGames.stream()
                .filter(g -> resultsByGame.containsKey(g) && !resultsByGame.get(g).isEmpty())
                .sorted(Comparator.comparing(Game::getId).reversed())
                .limit(5)
                .sorted(Comparator.comparing(Game::getId))
                .collect(Collectors.toList());

        System.out.printf("%nScore Calculation Debug — %s%n", player.getName());
        System.out.println("=".repeat(80));

        for (Game game : last5) {
            Result gameResult = allPlayerResults.stream()
                    .filter(r -> r.getSession().equals(game)).findFirst().orElse(null);

            // Rebuild the windowed buckets as updateMasterScore would see them at this game
            List<Result> filtered = LIMIT_TO_A_YEAR
                    ? allPlayerResults.stream()
                        .filter(r -> r.getSession().getDate().isAfter(game.getDate().minusYears(1)))
                        .collect(Collectors.toList())
                    : allPlayerResults;

            List<Result> recent = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) <= RECENT_WINDOW_SIZE)
                    .sorted(Comparator.comparing(r -> r.getSession().getId()))
                    .collect(Collectors.toList());
            List<Result> mid = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) > RECENT_WINDOW_SIZE
                            && (game.getId() - r.getSession().getId()) < RECENT_WINDOW_SIZE * 2)
                    .collect(Collectors.toList());
            List<Result> old = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) >= RECENT_WINDOW_SIZE * 2)
                    .collect(Collectors.toList());

            OptionalDouble midAvg = mid.stream().mapToDouble(Result::getScore).average();
            OptionalDouble oldAvg = old.stream().mapToDouble(Result::getScore).average();
            long nbResult = recent.size() + (midAvg.isPresent() ? 1 : 0) + (oldAvg.isPresent() ? 1 : 0);
            double masterScore = nbResult > 0
                    ? (recent.stream().mapToDouble(Result::getScore).sum()
                        + midAvg.orElse(0) + oldAvg.orElse(0)) / nbResult
                    : INITIAL_SCORE;

            String participation = gameResult != null
                    ? String.format("pos=%s  game_score=%s  pre=%s",
                        DF.format(gameResult.getResult()),
                        DF.format(gameResult.getScore()),
                        gameResult.getPlayerMasterScoreBeforeGame() != null
                            ? DF.format(gameResult.getPlayerMasterScoreBeforeGame()) : "-")
                    : "did not play";
            System.out.printf("%nGame #%d (%s)  %s  post=%s%n",
                    game.getId(), game.getDate(), participation, DF.format(masterScore));

            System.out.printf("  1-year cutoff: %s%n", game.getDate().minusYears(1));
            System.out.printf("  Recent  (gap 0-%d, %d entries): %s%n",
                    RECENT_WINDOW_SIZE, recent.size(),
                    recent.stream().map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  Mid avg (gap %d-%d, %d entries): %s%s%n",
                    RECENT_WINDOW_SIZE + 1, RECENT_WINDOW_SIZE * 2 - 1, mid.size(),
                    midAvg.isPresent() ? DF.format(midAvg.getAsDouble()) : "—",
                    mid.isEmpty() ? "" : " ← " + mid.stream()
                            .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  Old avg (gap >%d,  %d entries): %s%s%n",
                    RECENT_WINDOW_SIZE * 2, old.size(),
                    oldAvg.isPresent() ? DF.format(oldAvg.getAsDouble()) : "—",
                    old.isEmpty() ? "" : " ← " + old.stream()
                            .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  → (%d values) master = %s%n", nbResult, DF.format(masterScore));
        }
        System.out.println("=".repeat(80));
    }

    public void clearScoreCache() {
        if (scoreCacheDao == null) {
            log.error("No score cache configured");
            return;
        }
        scoreCacheDao.save(Collections.emptyMap());
        System.out.println("Score cache cleared. Run normally to recompute all scores from scratch.");
    }

    /**
     * One-time migration: reads Sheet8, strips leading 50s per player,
     * merges into the Computed Scores cache (Sheet8 wins on conflict), and saves.
     * Run once with: java -jar kob.jar --migrate-sheet8
     */
    public void migrateSheet8ToComputedScores() {
        if (!(scoreCacheDao instanceof dao.daoGSheet.GSheetScoreCacheDao)) {
            log.error("Migration requires GSheet DAO — current DAO type does not support Sheet8 reading");
            return;
        }
        dao.daoGSheet.GSheetScoreCacheDao gsheetCache = (dao.daoGSheet.GSheetScoreCacheDao) scoreCacheDao;

        log.info("Loading Sheet8 scores...");
        Map<Long, Map<String, Double>> sheet8 = gsheetCache.loadFromSheet8();

        log.info("Loading existing Computed Scores...");
        Map<Long, Map<String, Double>> existing = scoreCacheDao.load();

        // Merge: existing first, then Sheet8 overwrites (Sheet8 is authoritative)
        Map<Long, Map<String, Double>> merged = new LinkedHashMap<>(existing);
        for (Map.Entry<Long, Map<String, Double>> gameEntry : sheet8.entrySet()) {
            merged.computeIfAbsent(gameEntry.getKey(), k -> new HashMap<>())
                  .putAll(gameEntry.getValue());
        }

        int totalEntries = merged.values().stream().mapToInt(Map::size).sum();
        log.info("Saving merged cache: {} games, {} total entries", merged.size(), totalEntries);
        scoreCacheDao.save(merged);
        System.out.printf("Migration complete: %d games, %d player-score entries written to Computed Scores.%n",
                merged.size(), totalEntries);
    }

    private void initialize() {
        // Check if initialization is required.
    }

    public void printMasterScoresAtGame(long gameId) {
        List<Game> allGames = gameDao.getAllGames().stream()
                .sorted(Comparator.comparing(Game::getId))
                .collect(Collectors.toList());

        if (allGames.isEmpty()) {
            System.out.println("No games found.");
            return;
        }

        // Pre-load all results
        Map<Game, List<Result>> resultsByGame = new LinkedHashMap<>();
        for (Game game : allGames) {
            List<Result> results = resultDao.getAllResultsFromGame(game);
            if (results != null) resultsByGame.put(game, results);
        }

        // Full forward pass to get current master scores
        List<Player> allPlayers = playerDao.getAllPlayers();
        for (Player player : allPlayers) {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(INITIAL_SCORE));
        }

        // Look up the target game first so we know when to snapshot
        Game targetGame = allGames.stream().filter(g -> g.getId() == gameId).findFirst().orElse(null);
        if (targetGame == null) {
            System.out.println("Game ID " + gameId + " not found.");
            return;
        }

        Map<Player, List<Result>> playerResultHistory = new HashMap<>();
        Map<Player, BigDecimal> snapshotAfterGame = new HashMap<>();
        Map<Player, Boolean> snapshotHasResults = new HashMap<>();

        for (Game game : allGames) {
            List<Result> results = resultsByGame.getOrDefault(game, Collections.emptyList());
            if (results.isEmpty()) continue;

            List<Player> gamePlayers = results.stream()
                    .map(Result::getPlayer)
                    .sorted(Comparator.comparing(Player::getMasterScore).reversed())
                    .collect(Collectors.toList());

            int sizeForMargin = results.size() / QUARTILE_DIVISOR;
            OptionalDouble avg = gamePlayers.subList(0, sizeForMargin).stream()
                    .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageTop = avg.isPresent() ? avg.getAsDouble() + SCORE_RANGE_MARGIN : 0;
            if (averageTop < gamePlayers.get(0).getMasterScore().doubleValue())
                averageTop = gamePlayers.get(0).getMasterScore().doubleValue();
            avg = gamePlayers.subList(gamePlayers.size() - sizeForMargin, gamePlayers.size()).stream()
                    .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageBottom = avg.isPresent() ? avg.getAsDouble() - SCORE_RANGE_MARGIN : 0;
            if (averageBottom > gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue())
                averageBottom = gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue();
            game.setHighestPoint(averageTop);
            game.setLowestPoint(averageBottom);

            for (Result result : results) {
                double score = game.getHighestPoint() - ((result.getResult() - 1)
                        * (game.getHighestPoint() - game.getLowestPoint()) / (results.size() - 1));
                result.setScore(score);
                result.setDebutGame(!result.getPlayer().isHasResults());
                result.setPlayerMasterScoreBeforeGame(result.getPlayer().getMasterScore());
                playerResultHistory.computeIfAbsent(result.getPlayer(), k -> new ArrayList<>()).add(result);
            }

            for (Player player : allPlayers) {
                updateMasterScore(game, player, playerResultHistory.getOrDefault(player, Collections.emptyList()));
            }

            if (game.getId() == gameId) {
                for (Player player : allPlayers) {
                    snapshotAfterGame.put(player, player.getMasterScore());
                    snapshotHasResults.put(player, player.isHasResults());
                }
            }
        }

        Game lastGame = allGames.get(allGames.size() - 1);
        boolean withinYear = targetGame.getDate().isAfter(lastGame.getDate().minusYears(1));
        long ageInGames = lastGame.getId() - gameId;
        String bucket;
        if (!withinYear)                               bucket = "not used (>1 year old)";
        else if (ageInGames <= RECENT_WINDOW_SIZE)     bucket = "recent (counted individually)";
        else if (ageInGames < RECENT_WINDOW_SIZE * 2) bucket = "mid average";
        else                                           bucket = "old average";

        Map<Player, Result> gameResultMap = new HashMap<>();
        for (Result r : resultsByGame.getOrDefault(targetGame, Collections.emptyList()))
            gameResultMap.put(r.getPlayer(), r);

        int numPlayers = resultsByGame.getOrDefault(targetGame, Collections.emptyList()).size();
        double increment = numPlayers > 1
                ? (targetGame.getHighestPoint() - targetGame.getLowestPoint()) / (numPlayers - 1) : 0;

        System.out.println("Game ID " + gameId + " (" + targetGame.getDate() + ")  |  "
                + "Last game: " + lastGame.getId() + " (" + lastGame.getDate() + ")  |  "
                + "Contribution: " + bucket);
        List<String> participantNames = resultsByGame.getOrDefault(targetGame, Collections.emptyList()).stream()
                .sorted(Comparator.comparingDouble(Result::getResult))
                .map(r -> r.getPlayer().getName() + " (" + DF.format(r.getResult()) + ")")
                .collect(Collectors.toList());
        System.out.println("Max: " + DF.format(targetGame.getHighestPoint())
                + "  Min: " + DF.format(targetGame.getLowestPoint())
                + "  Increment: " + DF.format(increment)
                + "  Players: " + numPlayers);
        System.out.println("Participants: " + String.join(", ", participantNames));
        System.out.printf("%-3s %-25s %10s %14s %14s %16s%n", "#", "Player", "Position", "Pre-game Score", "Game Score", "Post-game Master");
        System.out.println("-".repeat(87));

        List<Player> ranked = allPlayers.stream()
                .filter(p -> Boolean.TRUE.equals(snapshotHasResults.get(p)))
                .sorted(Comparator.comparing(p -> snapshotAfterGame.getOrDefault(p, BigDecimal.ZERO).negate()))
                .collect(Collectors.toList());
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            Result r = gameResultMap.get(p);
            String position      = r != null ? DF.format(r.getResult()) : "-";
            String preGameScore  = r != null && r.getPlayerMasterScoreBeforeGame() != null ? DF.format(r.getPlayerMasterScoreBeforeGame()) : "-";
            String gameScore     = r != null ? (withinYear ? DF.format(r.getScore()) : "not used") : "-";
            String postScore     = DF.format(snapshotAfterGame.getOrDefault(p, BigDecimal.valueOf(INITIAL_SCORE)));
            System.out.printf("%-3d %-25s %10s %14s %14s %16s%n",
                    i + 1, p.getName(), position, preGameScore, gameScore, postScore);
        }
    }

    /**
     * Single forward pass over all games. Computes master score evolution and
     * returns the final ranked player list. O(N) instead of O(N²).
     */
    private List<Player> computeScoreEvolution(List<Game> allGames, Map<Game, List<Result>> resultsByGame,
            Map<Long, Map<String, Double>> cachedScores, Map<Long, Map<String, Double>> computedScores) {
        List<Player> allPlayers = playerDao.getAllPlayers();
        for (Player player : allPlayers) {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(INITIAL_SCORE));
        }

        Map<Player, List<Result>> playerResultHistory = new HashMap<>();
        int nbGames = 0;
        log.debug("Calculating master scores for all games: " + allGames.size());

        for (Game game : allGames) {
            long startTime = System.currentTimeMillis();
            List<Result> results = resultsByGame.getOrDefault(game, Collections.emptyList());

            if (!results.isEmpty()) {
                List<Player> gamePlayers = results.stream()
                        .map(Result::getPlayer)
                        .sorted(Comparator.comparing(Player::getMasterScore).reversed())
                        .collect(Collectors.toList());

                // Calculate score range for this game
                int sizeForMargin = results.size() / QUARTILE_DIVISOR;
                OptionalDouble avg = gamePlayers.subList(0, sizeForMargin).stream()
                        .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
                double averageTop = avg.isPresent() ? avg.getAsDouble() + SCORE_RANGE_MARGIN : 0;
                if (averageTop < gamePlayers.get(0).getMasterScore().doubleValue())
                    averageTop = gamePlayers.get(0).getMasterScore().doubleValue();
                avg = gamePlayers.subList(gamePlayers.size() - sizeForMargin, gamePlayers.size()).stream()
                        .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
                double averageBottom = avg.isPresent() ? avg.getAsDouble() - SCORE_RANGE_MARGIN: 0;
                if (averageBottom > gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue())
                    averageBottom = gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue();
                game.setHighestPoint(averageTop);
                game.setLowestPoint(averageBottom);

                // Assign scores and snapshot each player's pre-game master score
                for (Result result : results) {
                    double score = game.getHighestPoint() - ((result.getResult() - 1)
                            * (game.getHighestPoint() - game.getLowestPoint()) / (results.size() - 1));
                    result.setScore(score);
                    result.setDebutGame(!result.getPlayer().isHasResults());
                    result.setPlayerMasterScoreBeforeGame(result.getPlayer().getMasterScore());
                    playerResultHistory.computeIfAbsent(result.getPlayer(), k -> new ArrayList<>()).add(result);
                }

                // Update master scores for all players
                for (Player player : allPlayers) {
                    updateMasterScore(game, player, playerResultHistory.getOrDefault(player, Collections.emptyList()));
                }

                // Store post-game master score on each result for the dashboard (before cache override)
                for (Result result : results) {
                    result.setPlayerMasterScoreAfterGame(result.getPlayer().getMasterScore());
                }

                // Apply cached scores if available (preserves manual overrides)
                Map<String, Double> gameCache = cachedScores.get(game.getId());
                if (gameCache != null) {
                    for (Player player : allPlayers) {
                        Double cached = gameCache.get(player.getName());
                        if (cached != null) {
                            player.setMasterScore(BigDecimal.valueOf(cached));
                            player.setHasResults(true);
                        }
                    }
                }

                // Snapshot scores after this game for persistence
                Map<String, Double> snapshot = new HashMap<>();
                for (Player player : allPlayers) {
                    if (player.isHasResults())
                        snapshot.put(player.getName(), player.getMasterScore().doubleValue());
                }
                if (!snapshot.isEmpty()) computedScores.put(game.getId(), snapshot);

                // Record score evolution for this game
                List<Player> ranked = allPlayers.stream()
                        .filter(Player::isHasResults)
                        .sorted(Comparator.comparing(Player::getMasterScore).reversed())
                        .collect(Collectors.toList());
                for (Player player : ranked) {
                    List<String> evolution = masterScoresEvolution.getOrDefault(player, new ArrayList<>());
                    if (evolution.isEmpty()) {
                        for (int i = 0; i < nbGames; i++) evolution.add("");
                    }
                    evolution.add(player.getMasterScore().toString());
                    masterScoresEvolution.put(player, evolution);
                }

            }

            log.debug("Elapsed Time for game " + game.getId() + ": "
                    + (System.currentTimeMillis() - startTime) + " milliseconds");
            nbGames++;
        }

        log.debug("Calculation completed for master scores for all games: " + allGames.size());

        return allPlayers.stream()
                .filter(Player::isHasResults)
                .sorted(Comparator.comparing(Player::getMasterScore).reversed())
                .collect(Collectors.toList());
    }

    private void updateMasterScore(Game game, Player player, List<Result> allPlayerResults) {
        if (allPlayerResults.isEmpty()) {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(INITIAL_SCORE));
            return;
        }

        List<Result> filtered = allPlayerResults;
        if (LIMIT_TO_A_YEAR) {
            // Filter all results older than a year.
            filtered = allPlayerResults.stream()
                    .filter(result -> result.getSession().getDate().isAfter(game.getDate().minusYears(1)))
                    .collect(Collectors.toList());
        }

        // Individual results from the last 2 weeks counted as individual, one more
        // result is the average from game 9 to 16, and one more result is the average
        // from game 17 to up to a year.
        List<Result> lastTwoWeeks = filtered.stream()
                .filter(result -> result.getScore() != 0 && (game.getId() - result.getSession().getId()) <= RECENT_WINDOW_SIZE)
                .collect(Collectors.toList());

        OptionalDouble previousTwoWeeks = filtered.stream()
                .filter(result -> result.getScore() != 0
                        && (game.getId() - result.getSession().getId()) > RECENT_WINDOW_SIZE
                        && (game.getId() - result.getSession().getId()) < RECENT_WINDOW_SIZE * 2)
                .mapToDouble(Result::getScore).average();
        OptionalDouble rest = filtered.stream()
                .filter(result -> result.getScore() != 0
                        && (game.getId() - result.getSession().getId()) >= RECENT_WINDOW_SIZE * 2)
                .mapToDouble(Result::getScore).average();

        long nbResult = lastTwoWeeks.size() + (previousTwoWeeks.isPresent() ? 1 : 0) + (rest.isPresent() ? 1 : 0);

        if (nbResult > 0) {
            double masterScore = (lastTwoWeeks.stream().mapToDouble(Result::getScore).sum()
                    + (previousTwoWeeks.isPresent() ? previousTwoWeeks.getAsDouble() : 0)
                    + (rest.isPresent() ? rest.getAsDouble() : 0)) / nbResult;
            player.setMasterScore(BigDecimal.valueOf(masterScore));
            player.setHasResults(true);
        } else {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(INITIAL_SCORE));
        }
    }
}
