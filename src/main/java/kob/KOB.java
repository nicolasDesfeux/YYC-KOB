package kob;

import dao.daoGSheet.GSheetConnector;
import dao.daoGSheet.GameDaoGSheet;
import dao.daoGSheet.PlayerDaoGSheet;
import dao.daoGSheet.ResultDaoGSheet;
import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.ResultDao;
import dao.daoJdbc.GameDaoJDBC;
import dao.daoJdbc.PlayerDaoJDBC;
import dao.daoJdbc.ResultDaoJDBC;
import dto.Game;
import dto.Player;
import dto.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the main application class. It queries and utilizes the different objects from the model to display rankings statistics etc
 */
public class KOB {

    private static final Logger log = LogManager.getLogger(KOB.class);

    // TODO Move those settings to properties database.
    public final static int INITIAL_SCORE = 50;
    public static final int MINIMUM_NB_PLAYERS = 8;
    //public final static int INITIAL_BUYIN = 5;
    private final ResultDao resultDao;
    private final PlayerDao playerDao;
    // Objects to access data.
    private final GameDao gameDao;
    public final static DecimalFormat DF = new DecimalFormat("0.0");
    // TODO this is a bit annoying, because now everytime we add a game, we need to get ride of older games, and recalculate everything...
    public final static boolean LIMIT_TO_A_YEAR = true;

    private static KOB single_instance = null;
    private final Map<Player, List<String>> masterScoresEvolution = new HashMap<>();

    /**
     * Starts the system, and sets up data access. It also initializes the data if need be.
     */
    public KOB() {
        Properties properties = new Properties();
        String daoType = "";
        try {
            log.debug("Loading properties files");
            InputStream input = KOB.class.getResourceAsStream("/config.properties");
            // Load the properties file
            properties.load(input);

            // Get values using keys
            daoType = properties.getProperty("dao.type");
            assert input != null;
            input.close();

        } catch (IOException e) {
            log.error("Could not load properties ", e);
        }
        log.debug("Properties loaded");

        if (daoType.equals("GSheet")) {
            log.debug("Connecting to GSheet");
            this.gameDao = new GameDaoGSheet();
            this.playerDao = new PlayerDaoGSheet();
            this.resultDao = new ResultDaoGSheet(gameDao, playerDao);
        } else {
            this.gameDao = new GameDaoJDBC();
            this.resultDao = new ResultDaoJDBC();
            this.playerDao = new PlayerDaoJDBC();
        }
        this.initialize();
    }

    // static method to create instance of Singleton class
    public static KOB getInstance() {
        if (single_instance == null) single_instance = new KOB();

        return single_instance;
    }

    public static void main(String[] args) {
        // Read all results from
        KOB kob = getInstance();
        kob.persistRanking();
    }

    void persistRanking() {
        //log.debug("Persisting the following ranking: \n" + this.getPrintableRanking());
        this.getMasterScoreEvolution();
        if (playerDao instanceof PlayerDaoGSheet) {
            log.debug("Writing ranking to GSheet");
            GSheetConnector.writeRanking(this.getPlayersRankingAtGame(this.gameDao.getLastCompletedGame()));
            log.debug("Writing Master Scores to GSheet");
            GSheetConnector.writeMasterScores(masterScoresEvolution);
        } else System.out.println(this.getPrintableRanking());
    }

    private void getMasterScoreEvolution() {
        int nbGames = 0;
        log.debug("Calculating master scores for all game " + gameDao.getAllGames().size());

        for (Game allGame : gameDao.getAllGames()) {
            long startTime = System.currentTimeMillis();
            List<Player> playersRankingAtGame = getPlayersRankingAtGame(allGame);
            for (Player player : playersRankingAtGame) {
                List<String> playerEvolution = masterScoresEvolution.getOrDefault(player, new ArrayList<>());
                if (playerEvolution.isEmpty()) {
                    for (int i = 0; i < nbGames; i++) {
                        playerEvolution.add("");
                    }
                    //Fill up with existing games
                }
                playerEvolution.add(player.getMasterScore().toString());
                masterScoresEvolution.put(player, playerEvolution);
            }
            nbGames++;
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            log.debug("Elapsed Time for game " + allGame.getId() + ": " + elapsedTime + " milliseconds");
        }
        log.debug("Calculation completed for master scores for all game " + gameDao.getAllGames().size());
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

    /*public String printTierStats() {
        String statsString = "";
        List<Result> results = resultDao.getAllResults();
        List<List<Player>> tmp = new ArrayList<>();
        for (Result result : results) {
            if ((result.getResult() - 1) % 4 == 0) {
                while (tmp.size() < result.getResult()) {
                    tmp.add(null);
                    tmp.add(null);
                    tmp.add(null);
                    tmp.add(null);
                }
                List<Player> newList = tmp.get(Math.toIntExact(result.getResult() - 1)) == null ? new ArrayList<>() : tmp.get(Math.toIntExact(result.getResult() - 1));
                newList.add(result.getPlayer());
                tmp.set(Math.toIntExact(result.getResult() - 1), newList);
            }
        }

        for (int i = 0; i < tmp.size(); i = i + 4) {
            System.out.println("Tier " + (i / 4 + 1) + " statistics: ");
            while (tmp.get(i).size() > 0) {
                Player top = mostCommon(tmp.get(i));
                int finalI = i;
                long nbGames = resultDao.getAllResultsFromPlayer(top).stream().filter(result -> result.getResult() >= (finalI + 1) && result.getResult() < (finalI + 5)).count();
                int count = 0;
                for (Player player : tmp.get(i)) {
                    if (player.equals(top)) {
                        count++;
                    }
                }
                if (top != null) {
                    statsString +=top.getName() + ": " + Math.toIntExact(count * 100 / nbGames) + "% (" + count + " victories in " + nbGames + " appearances)appearances\n";
                }
                while (tmp.get(i).contains(top)) {
                    tmp.get(i).remove(top);
                }
            }

        }
        return statsString;
    }

    public static <T> T mostCommon(List<T> list) {
        Map<T, Integer> map = new HashMap<>();

        for (T t : list) {
            Integer val = map.get(t);
            map.put(t, val == null ? 1 : val + 1);
        }

        Map.Entry<T, Integer> max = null;

        for (Map.Entry<T, Integer> e : map.entrySet()) {
            if (max == null || e.getValue() > max.getValue())
                max = e;
        }

        return max == null ? null : max.getKey();
    }*/

    /*public String printPlayerStats() {
        //Players with the most games
        int nbGames = gameDao.getAllCompleteGames().size();
        Map<Player,Integer> mostRegularPlayers = new HashMap<>();
        for (Player player : playerDao.getAllPlayers()) {
            mostRegularPlayers.put(player, resultDao.getAllResultsFromPlayer(player).size());
        }
        mostRegularPlayers = mostRegularPlayers.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        StringBuilder appearanceString = new StringBuilder();
        appearanceString.append("Most regular players: ")
                .append(mostRegularPlayers.size() > 1 ? "s (" : " (");

        for (Player mostRegularPlayer : mostRegularPlayers.keySet()) {
            appearanceString.append(mostRegularPlayer.getName()).append("\n")
                    .append(mostRegularPlayers.get(mostRegularPlayer))
                    .append(" out of ")
                    .append(nbGames)
                    .append(" games): \n");
        }
        return appearanceString.toString();
    }*/

    private void initialize() {
        // Check if initialization is required.
    }

    /**
     * This method return the current players ranking, and generates a String to display it.
     *
     * @return A formatted string that shows the current player ranking.
     */
    public String getPrintableRanking() {
        return getPrintableRankingAtGame(gameDao.getLastCompletedGame());
    }

    public String getPrintableRankingAtGame(Game game) {
        List<Player> rankings = getPlayersRankingAtGame(game);
        StringBuilder rankingString = new StringBuilder();
        for (int i = 0; i < rankings.size(); i++) {
            rankingString.append(i + 1).append(". ").append(rankings.get(i).getName()).append(" (").append(DF.format(rankings.get(i).getMasterScore())).append(")\n");
        }
        return rankingString.toString();
    }


    private List<Player> getPlayersRankingAtGame(Game game) {
        //Get all the games before (inclusive) of the one in parameter
        List<Game> games = gameDao.getAllGames().stream().filter(a -> a.equals(game) || a.getDate().isBefore(game.getDate())).collect(Collectors.toList());
        // Always start at initial score
        for (Player player : playerDao.getAllPlayers()) {
            player.setHasResults(false);
            player.setMasterScore(BigDecimal.valueOf(INITIAL_SCORE));
        }

        //Make sure we are looking at games starting at the oldest ones.
        games.sort(Comparator.comparing(Game::getId));
        for (Game currentGame : games) {
            // Get all the results from that game
            List<Result> results = resultDao.getAllResultsFromGame(currentGame);
            List<Player> gamePlayers = new ArrayList<>();
            for (Result result : results) {
                gamePlayers.add(result.getPlayer());
            }

            // Sort the players by master scores
            gamePlayers.sort(Comparator.comparing(Player::getMasterScore));
            Collections.reverse(gamePlayers);
            // Calculate the min and max point for each game.
            // Max: Top 25% average + 10, unless the highest player has more
            // Min: Bottom 25% average - 10, unless the lowest player has less
            int sizeForMarging = results.size() / 4;
            OptionalDouble average = gamePlayers.subList(0, sizeForMarging).stream().mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageTop = average.isPresent() ? average.getAsDouble() + 10 : 0;
            if (averageTop < gamePlayers.get(0).getMasterScore().doubleValue())
                averageTop = gamePlayers.get(0).getMasterScore().doubleValue();
            average = gamePlayers.subList(gamePlayers.size() - sizeForMarging, gamePlayers.size()).stream().mapToDouble(p -> p.getMasterScore().doubleValue()).average();
            double averageBottom = average.isPresent() ? average.getAsDouble() - 10 : 0;
            if (averageBottom > gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue()) {
                averageBottom = gamePlayers.get(gamePlayers.size() - 1).getMasterScore().doubleValue();
            }
            currentGame.setHighestPoint(averageTop);
            currentGame.setLowestPoint(averageBottom);
            // Done min/max

            // Set game scores
            for (Result result : results) {
                double score = currentGame.getHighestPoint() - ((result.getResult() - 1) * (currentGame.getHighestPoint() - currentGame.getLowestPoint()) / (results.size() - 1));
                result.setScore(score);
                // Save the player's master score before applying the new result to the master score.
                result.setPlayerMasterScoreBeforeGame(result.getPlayer().getMasterScore());
            }

            // Set new master scores
            // Update the master scores for all players
            for (Player player : playerDao.getAllPlayers()) {
                this.setPlayerMasterScoreAtGame(currentGame, player);
            }

        }
        List<Player> players = playerDao.getAllPlayers().stream().filter(Player::isHasResults).sorted(Player::compare).collect(Collectors.toList());
        Collections.reverse(players);
        return players;
    }

    private void setPlayerMasterScoreAtGame(Game game, Player player) {
        // Get all the results for a player
        List<Result> allPlayerResults = resultDao.getAllResultsFromPlayer(player);
        if (allPlayerResults != null) {
            if (KOB.LIMIT_TO_A_YEAR) {
                // Filter all results older than a year.
                allPlayerResults = allPlayerResults.stream().filter(result -> result.getSession().getDate().isAfter(game.getDate().minusYears(1))).collect(Collectors.toList());
            }

            // Only look at results that are up to the game at hand.
            allPlayerResults = allPlayerResults.stream().filter(result -> result.getSession().getId() <= game.getId()).collect(Collectors.toList());

            List<Result> lastTwoWeeks = allPlayerResults.stream().filter(result -> result.getScore() != 0 && (result.getSession().getId() - game.getId()) < 9).collect(Collectors.toList());

            OptionalDouble previousTwoWeeks = allPlayerResults.stream().filter(result -> result.getScore() != 0 && (result.getSession().getId() - game.getId()) > 8 && (result.getSession().getId() - game.getId()) < 17).mapToDouble(Result::getScore).average();
            OptionalDouble rest = allPlayerResults.stream().filter(result -> result.getScore() != 0 && (result.getSession().getId() - game.getId()) > 16).mapToDouble(Result::getScore).average();


            long nbResult = lastTwoWeeks.size() + (previousTwoWeeks.isPresent() ? 1 : 0) + (rest.isPresent() ? 1 : 0);

            if (nbResult > 0) {
                // Individual results from the last 2 weeks counted a individual, one more result is the average from game 9 to 16, and one more result is the average from game 17 to up to a year.
                // Maximum 10 results
                assert nbResult <= 10;
                double masterScore = (lastTwoWeeks.stream().mapToDouble(Result::getScore).sum() + (previousTwoWeeks.isPresent() ? previousTwoWeeks.getAsDouble() : 0) + (rest.isPresent() ? rest.getAsDouble() : 0)) / nbResult;

                player.setMasterScore(BigDecimal.valueOf(masterScore));
                player.setHasResults(true);
            } else {
                player.setHasResults(false);
            }
        }

    }


}
