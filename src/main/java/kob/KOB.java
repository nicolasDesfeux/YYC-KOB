package kob;

import dao.DaoFactory;
import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.RankingWriter;
import dao.daoInterface.ResultDao;
import dao.daoInterface.GameInputDao;
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

    /**
     * Tunable scoring parameters, loaded from config.properties at startup.
     * Defaults apply until the first KOB instance is constructed, so DAOs that
     * read this lazily always see a usable configuration.
     */
    private static volatile KobConfig config = KobConfig.defaults();

    /** The active scoring configuration. */
    public static KobConfig config() { return config; }
    private final ResultDao resultDao;
    private final PlayerDao playerDao;
    private final GameDao gameDao;
    private final RankingWriter rankingWriter;
    private final ScoreCacheDao scoreCacheDao;
    private final GameInputDao gameInputDao;
    private String debugPlayer;   // optional: dump bucket breakdown for one player
    private String htmlOutputPath;
    public final static DecimalFormat DF = new DecimalFormat("0.0");
    
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
            config = KobConfig.fromProperties(properties);
            log.info("Scoring configuration: {}", config);

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
        this.gameInputDao  = factory.createGameInputDao();
        this.initialize();
    }

    // static method to create instance of Singleton class
    public static synchronized KOB getInstance() {
        if (singleInstance == null)
            singleInstance = new KOB();
        return singleInstance;
    }

    public void addGameFromInput() {
        if (gameInputDao != null) {
            log.info("Processing game input from staging sheet...");
            gameInputDao.processInput();
            log.info("Game input processing complete");
        } else {
            log.error("GameInputDao not available for this DAO type");
        }
    }

    public static void main(String[] args) {
        KOB kob = getInstance();
        // --debug-player "Name" may appear anywhere; strip it before dispatching
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        int dp = argList.indexOf("--debug-player");
        if (dp >= 0 && dp + 1 < argList.size()) {
            kob.debugPlayer = argList.get(dp + 1);
            argList.subList(dp, dp + 2).clear();
        }
        args = argList.toArray(new String[0]);

        if (args.length > 0 && args[0].equals("--clear-cache")) {
            kob.clearScoreCache();
        } else if (args.length > 1 && args[0].equals("--as-of")) {
            kob.addGameFromInput();
            kob.persistRanking(java.time.LocalDate.parse(args[1]));
        } else if (args.length > 0 && args[0].equals("--compare")) {
            kob.addGameFromInput();
            kob.compareWithExpected();
        } else {
            kob.addGameFromInput();
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
        final List<Game> games = allGames;

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
        java.time.LocalDate referenceDate = asOfDate != null ? asOfDate : java.time.LocalDate.now();
        List<Player> finalRanking = computeScoreEvolution(allGames, resultsByGame, cachedScores, computedScores, referenceDate);
        if (scoreCacheDao != null && asOfDate == null) scoreCacheDao.save(computedScores);

        java.time.LocalDate cutoff = referenceDate.minusYears(1);
        List<Player> ranking = finalRanking.stream()
                .filter(player -> {
                    List<Result> results = resultsByPlayer.getOrDefault(player, Collections.emptyList());
                    return results.stream()
                            .anyMatch(r -> r.getSession().getDate().isAfter(cutoff));
                })
                .collect(Collectors.toList());

        rankingWriter.writeRanking(ranking, masterScoresEvolution);
        printLastGamesDebug(allGames, resultsByGame, referenceDate);
        if (debugPlayer != null) {
            finalRanking.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(debugPlayer))
                    .findFirst()
                    .ifPresentOrElse(
                            p -> printTopPlayerBucketDebug(p, games, resultsByGame, referenceDate),
                            () -> log.warn("--debug-player: no ranked player named '{}'", debugPlayer));
        }

        ComputedStats stats = new StatisticsComputer(allGames, resultsByGame).compute();
        rankingWriter.writeStatistics(stats.global, stats.players);

        try {
            new dao.HtmlWriter().write(htmlOutputPath, ranking, stats.global, stats.players, allGames, resultsByGame, computedScores);
            log.info("HTML dashboard written to {}", htmlOutputPath);
        } catch (IOException e) {
            log.error("Failed to write HTML dashboard", e);
        }
    }

    private void printLastGamesDebug(List<Game> allGames, Map<Game, List<Result>> resultsByGame,
                                      java.time.LocalDate referenceDate) {
        List<Game> last5 = allGames.stream()
                .filter(g -> resultsByGame.containsKey(g) && !resultsByGame.get(g).isEmpty())
                .sorted(Comparator.comparing(Game::getId).reversed())
                .limit(5)
                .sorted(Comparator.comparing(Game::getId))
                .collect(Collectors.toList());

        // Build per-player result history up to each game for post-game master score lookup
        // We use playerMasterScoreAfterGame which was set during computeScoreEvolution
        for (Game game : last5) {
            List<Result> results = resultsByGame.get(game);
            if (results == null || results.isEmpty()) continue;

            int n = results.size();
            int q = n / config.quartileDivisor;
            List<Result> byFinish = results.stream()
                    .sorted(Comparator.comparingDouble(Result::getResult))
                    .collect(Collectors.toList());

            // top/bottom 25% averages of pre-game master scores (same as scoring engine)
            double topAvg = results.stream()
                    .sorted(Comparator.comparing(r -> r.getPlayerMasterScoreBeforeGame() == null
                            ? BigDecimal.ZERO : r.getPlayerMasterScoreBeforeGame().negate()))
                    .limit(q)
                    .mapToDouble(r -> r.getPlayerMasterScoreBeforeGame() == null ? 0
                            : r.getPlayerMasterScoreBeforeGame().doubleValue())
                    .average().orElse(0);
            double botAvg = results.stream()
                    .sorted(Comparator.comparing(r -> r.getPlayerMasterScoreBeforeGame() == null
                            ? BigDecimal.ZERO : r.getPlayerMasterScoreBeforeGame()))
                    .limit(q)
                    .mapToDouble(r -> r.getPlayerMasterScoreBeforeGame() == null ? 0
                            : r.getPlayerMasterScoreBeforeGame().doubleValue())
                    .average().orElse(0);

            System.out.printf("%n%-60s%n", "=".repeat(60));
            System.out.printf("Game #%d  |  %s  |  %d attendees%n", game.getId(), game.getDate(), n);
            System.out.printf("  Top 25%% avg MS: %-6s  Bot 25%% avg MS: %-6s%n",
                    DF.format(topAvg), DF.format(botAvg));
            System.out.printf("  Max score: %-6s  Min score: %-6s%n",
                    DF.format(game.getHighestPoint()), DF.format(game.getLowestPoint()));
            System.out.println("-".repeat(60));
            System.out.printf("  %-4s %-22s %8s %8s %8s%n",
                    "Pos", "Player", "Pre-MS", "G-Score", "Post-MS");
            System.out.println("  " + "-".repeat(56));
            for (Result r : byFinish) {
                String preMs  = r.getPlayerMasterScoreBeforeGame() != null
                        ? DF.format(r.getPlayerMasterScoreBeforeGame()) : "-";
                String postMs = r.getPlayerMasterScoreAfterGame() != null
                        ? DF.format(r.getPlayerMasterScoreAfterGame()) : "-";
                System.out.printf("  %-4s %-22s %8s %8s %8s%n",
                        DF.format(r.getResult()), r.getPlayer().getName(),
                        preMs, DF.format(r.getScore()), postMs);
            }
            System.out.println("-".repeat(60));

            // Post-game ranking (players with results, sorted by post-game MS)
            List<Result> ranked = results.stream()
                    .filter(r -> r.getPlayerMasterScoreAfterGame() != null)
                    .sorted(Comparator.comparing(r -> r.getPlayerMasterScoreAfterGame().negate()))
                    .collect(Collectors.toList());
            System.out.printf("  Post-game ranking (participants only):%n");
            for (int i = 0; i < ranked.size(); i++) {
                Result r = ranked.get(i);
                System.out.printf("    %2d. %-22s %s%n",
                        i + 1, r.getPlayer().getName(),
                        DF.format(r.getPlayerMasterScoreAfterGame()));
            }
        }
        System.out.println("=".repeat(60));
    }

    private void printTopPlayerBucketDebug(Player player, List<Game> allGames,
                                            Map<Game, List<Result>> resultsByGame,
                                            java.time.LocalDate referenceDate) {
        List<Result> allPlayerResults = allGames.stream()
                .map(g -> resultsByGame.getOrDefault(g, Collections.emptyList()))
                .flatMap(List::stream)
                .filter(r -> r.getPlayer().equals(player))
                .collect(Collectors.toList());

        Game lastGame = allGames.stream()
                .filter(g -> resultsByGame.containsKey(g) && !resultsByGame.get(g).isEmpty())
                .max(Comparator.comparing(Game::getId)).orElse(null);
        if (lastGame == null) return;

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

            List<Result> filtered = config.limitToAYear
                    ? allPlayerResults.stream()
                        .filter(r -> r.getSession().getDate().isAfter(game.getDate().minusYears(1)))
                        .collect(Collectors.toList())
                    : allPlayerResults;

            List<Result> recent = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) <= config.recentWindowSize)
                    .sorted(Comparator.comparing(r -> r.getSession().getId()))
                    .collect(Collectors.toList());
            List<Result> mid = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) > config.recentWindowSize
                            && (game.getId() - r.getSession().getId()) < config.recentWindowSize * 2)
                    .collect(Collectors.toList());
            List<Result> old = filtered.stream()
                    .filter(r -> r.getScore() != 0
                            && (game.getId() - r.getSession().getId()) >= config.recentWindowSize * 2)
                    .collect(Collectors.toList());

            OptionalDouble midAvg = mid.stream().mapToDouble(Result::getScore).average();
            OptionalDouble oldAvg = old.stream().mapToDouble(Result::getScore).average();
            long nbResult = recent.size() + (midAvg.isPresent() ? 1 : 0) + (oldAvg.isPresent() ? 1 : 0);
            double masterScore = nbResult > 0
                    ? (recent.stream().mapToDouble(Result::getScore).sum()
                        + midAvg.orElse(0) + oldAvg.orElse(0)) / nbResult
                    : config.initialScore;

            String participation = gameResult != null
                    ? String.format("pos=%s  game_score=%s  pre=%s",
                        DF.format(gameResult.getResult()),
                        DF.format(gameResult.getScore()),
                        gameResult.getPlayerMasterScoreBeforeGame() != null
                            ? DF.format(gameResult.getPlayerMasterScoreBeforeGame()) : "-")
                    : "did not play";
            System.out.printf("%nGame #%d (%s)  %s  post=%s  [max=%s  min=%s]%n",
                    game.getId(), game.getDate(), participation, DF.format(masterScore),
                    DF.format(game.getHighestPoint()), DF.format(game.getLowestPoint()));
            System.out.printf("  1-year cutoff: %s%n", game.getDate().minusYears(1));
            System.out.printf("  Recent  (gap 0-%d, %d entries): %s%n",
                    config.recentWindowSize, recent.size(),
                    recent.stream().map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  Mid avg (gap %d-%d, %d entries): %s%s%n",
                    config.recentWindowSize + 1, config.recentWindowSize * 2 - 1, mid.size(),
                    midAvg.isPresent() ? DF.format(midAvg.getAsDouble()) : "—",
                    mid.isEmpty() ? "" : " ← " + mid.stream()
                            .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  Old avg (gap >%d,  %d entries): %s%s%n",
                    config.recentWindowSize * 2, old.size(),
                    oldAvg.isPresent() ? DF.format(oldAvg.getAsDouble()) : "—",
                    old.isEmpty() ? "" : " ← " + old.stream()
                            .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                            .collect(Collectors.joining(", ")));
            System.out.printf("  → (%d values) master = %s%n", nbResult, DF.format(masterScore));
        }

        // Final snapshot: same calculation as the live ranking uses (referenceDate cutoff)
        System.out.printf("%nAs of %s (1-year cutoff: %s)%n", referenceDate, referenceDate.minusYears(1));
        List<Result> filteredToday = config.limitToAYear
                ? allPlayerResults.stream()
                    .filter(r -> r.getSession().getDate().isAfter(referenceDate.minusYears(1)))
                    .collect(Collectors.toList())
                : allPlayerResults;
        List<Result> recentToday = filteredToday.stream()
                .filter(r -> r.getScore() != 0 && (lastGame.getId() - r.getSession().getId()) <= config.recentWindowSize)
                .sorted(Comparator.comparing(r -> r.getSession().getId()))
                .collect(Collectors.toList());
        List<Result> midToday = filteredToday.stream()
                .filter(r -> r.getScore() != 0
                        && (lastGame.getId() - r.getSession().getId()) > config.recentWindowSize
                        && (lastGame.getId() - r.getSession().getId()) < config.recentWindowSize * 2)
                .collect(Collectors.toList());
        List<Result> oldToday = filteredToday.stream()
                .filter(r -> r.getScore() != 0
                        && (lastGame.getId() - r.getSession().getId()) >= config.recentWindowSize * 2)
                .collect(Collectors.toList());
        OptionalDouble midAvgToday = midToday.stream().mapToDouble(Result::getScore).average();
        OptionalDouble oldAvgToday = oldToday.stream().mapToDouble(Result::getScore).average();
        long nbToday = recentToday.size() + (midAvgToday.isPresent() ? 1 : 0) + (oldAvgToday.isPresent() ? 1 : 0);
        double masterToday = nbToday > 0
                ? (recentToday.stream().mapToDouble(Result::getScore).sum()
                    + midAvgToday.orElse(0) + oldAvgToday.orElse(0)) / nbToday
                : config.initialScore;
        System.out.printf("  Recent  (gap 0-%d, %d entries): %s%n",
                config.recentWindowSize, recentToday.size(),
                recentToday.stream().map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  Mid avg (gap %d-%d, %d entries): %s%s%n",
                config.recentWindowSize + 1, config.recentWindowSize * 2 - 1, midToday.size(),
                midAvgToday.isPresent() ? DF.format(midAvgToday.getAsDouble()) : "—",
                midToday.isEmpty() ? "" : " ← " + midToday.stream()
                        .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  Old avg (gap >%d,  %d entries): %s%s%n",
                config.recentWindowSize * 2, oldToday.size(),
                oldAvgToday.isPresent() ? DF.format(oldAvgToday.getAsDouble()) : "—",
                oldToday.isEmpty() ? "" : " ← " + oldToday.stream()
                        .map(r -> String.format("G%d[%s]=%s", r.getSession().getId(), r.getSession().getDate(), DF.format(r.getScore())))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  → (%d values) master = %s%n", nbToday, DF.format(masterToday));
        System.out.println("=".repeat(80));
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

    public void compareWithExpected() {
        if (!(scoreCacheDao instanceof dao.daoGSheet.GSheetScoreCacheDao)) {
            log.error("--compare requires GSheet DAO");
            return;
        }
        dao.daoGSheet.GSheetScoreCacheDao gsheetCache = (dao.daoGSheet.GSheetScoreCacheDao) scoreCacheDao;

        Map<Long, Map<String, Double>> expected = gsheetCache.loadFromSheet8();
        if (expected.isEmpty()) {
            System.out.println("No reference scores found in Sheet8.");
            return;
        }

        List<Game> allGames = gameDao.getAllGames();
        allGames.sort(Comparator.comparing(Game::getId));
        Map<Game, List<Result>> resultsByGame = new LinkedHashMap<>();
        for (Game game : allGames) {
            List<Result> results = resultDao.getAllResultsFromGame(game);
            if (results != null) resultsByGame.put(game, results);
        }
        Map<Long, Map<String, Double>> computed = new LinkedHashMap<>();
        computeScoreEvolution(allGames, resultsByGame, Collections.emptyMap(), computed, java.time.LocalDate.now());

        // Only compare games present in Sheet8
        List<Long> compareGames = expected.keySet().stream()
                .filter(computed::containsKey)
                .sorted()
                .collect(Collectors.toList());

        System.out.printf("%nAlgorithm Accuracy vs Sheet8 — %d games%n", compareGames.size());
        System.out.println("=".repeat(72));
        System.out.printf("%-8s  %6s  %6s  %6s  %-30s%n",
                "Game", "MAE", "MaxErr", "Players", "Worst outlier");
        System.out.println("-".repeat(72));

        double totalMAE = 0;
        int totalGames = 0;
        for (Long gameId : compareGames) {
            Map<String, Double> exp = expected.get(gameId);
            Map<String, Double> calc = computed.get(gameId);
            if (exp == null || calc == null) continue;

            double sumErr = 0;
            double maxErr = 0;
            String worstPlayer = "";
            int n = 0;
            for (Map.Entry<String, Double> e : exp.entrySet()) {
                Double calcScore = calc.get(e.getKey());
                if (calcScore == null) continue;
                double err = Math.abs(e.getValue() - calcScore);
                sumErr += err;
                n++;
                if (err > maxErr) { maxErr = err; worstPlayer = e.getKey(); }
            }
            if (n == 0) continue;
            double mae = sumErr / n;
            totalMAE += mae;
            totalGames++;
            System.out.printf("G%-7d  %6.2f  %6.2f  %6d  %s (%.2f vs %.2f)%n",
                    gameId, mae, maxErr, n, worstPlayer,
                    calc.getOrDefault(worstPlayer, 0.0), exp.getOrDefault(worstPlayer, 0.0));
        }
        System.out.println("-".repeat(72));
        if (totalGames > 0)
            System.out.printf("Overall MAE across %d games: %.3f%n", totalGames, totalMAE / totalGames);
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
            player.setMasterScore(BigDecimal.valueOf(config.initialScore));
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

            int sizeForMargin = results.size() / config.quartileDivisor;
            OptionalDouble avg = gamePlayers.subList(0, sizeForMargin).stream()
                    .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageTop = avg.isPresent() ? avg.getAsDouble() + config.scoreRangeMargin : 0;
            if (averageTop < gamePlayers.get(0).getMasterScore().doubleValue())
                averageTop = gamePlayers.get(0).getMasterScore().doubleValue();
            avg = gamePlayers.subList(gamePlayers.size() - sizeForMargin, gamePlayers.size()).stream()
                    .mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageBottom = avg.isPresent() ? avg.getAsDouble() - config.scoreRangeMargin : 0;
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
                updateMasterScore(game, game.getDate(), player, playerResultHistory.getOrDefault(player, Collections.emptyList()));
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
        else if (ageInGames <= config.recentWindowSize)     bucket = "recent (counted individually)";
        else if (ageInGames < config.recentWindowSize * 2) bucket = "mid average";
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
            String postScore     = DF.format(snapshotAfterGame.getOrDefault(p, BigDecimal.valueOf(config.initialScore)));
            System.out.printf("%-3d %-25s %10s %14s %14s %16s%n",
                    i + 1, p.getName(), position, preGameScore, gameScore, postScore);
        }
    }

    /**
     * Single forward pass over all games. Computes master score evolution and
     * returns the final ranked player list. O(N) instead of O(N²).
     */
    private List<Player> computeScoreEvolution(List<Game> allGames, Map<Game, List<Result>> resultsByGame,
            Map<Long, Map<String, Double>> cachedScores, Map<Long, Map<String, Double>> computedScores,
            java.time.LocalDate referenceDate) {
        List<Player> allPlayers = playerDao.getAllPlayers();
        for (Player player : allPlayers) {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(config.initialScore));
        }

        Map<Player, List<Result>> playerResultHistory = new HashMap<>();
        int nbGames = 0;

        for (Game game : allGames) {
            List<Result> results = resultsByGame.getOrDefault(game, Collections.emptyList());

            if (!results.isEmpty()) {
                List<Player> gamePlayers = results.stream()
                        .map(Result::getPlayer)
                        .collect(Collectors.toList());

                ScoringEngine.ScoreRange range = ScoringEngine.computeScoreRange(
                        ScoringEngine.descendingScores(gamePlayers), config);
                game.setHighestPoint(range.highest());
                game.setLowestPoint(range.lowest());

                // Assign scores and snapshot each player's pre-game master score
                for (Result result : results) {
                    double score = ScoringEngine.gameScore(result.getResult(), range, results.size());
                    result.setScore(score);
                    result.setDebutGame(!result.getPlayer().isHasResults());
                    result.setPlayerMasterScoreBeforeGame(result.getPlayer().getMasterScore());
                    playerResultHistory.computeIfAbsent(result.getPlayer(), k -> new ArrayList<>()).add(result);
                }

                // Update master scores for all players
                for (Player player : allPlayers) {
                    updateMasterScore(game, game.getDate(), player, playerResultHistory.getOrDefault(player, Collections.emptyList()));
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

            nbGames++;
        }

        // Final recalculation using referenceDate (today or as-of) so the 1-year
        // cutoff is correct at query time, not pinned to the last game's date.
        Game lastGame = allGames.get(allGames.size() - 1);
        for (Player player : allPlayers) {
            updateMasterScore(lastGame, referenceDate, player,
                    playerResultHistory.getOrDefault(player, Collections.emptyList()));
        }

        return allPlayers.stream()
                .filter(Player::isHasResults)
                .sorted(Comparator.comparing(Player::getMasterScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Recomputes one player's master score as of {@code game}, using
     * {@code referenceDate} for the one-year window. The arithmetic lives in
     * {@link ScoringEngine} so the rules stay directly testable.
     */
    private void updateMasterScore(Game game, java.time.LocalDate referenceDate, Player player,
                                   List<Result> allPlayerResults) {
        double score = ScoringEngine.masterScore(
                allPlayerResults, game.getId(), referenceDate, config);
        player.setMasterScore(BigDecimal.valueOf(score));
        player.setHasResults(ScoringEngine.hasQualifyingResults(
                allPlayerResults, game.getId(), referenceDate, config));
    }
}
