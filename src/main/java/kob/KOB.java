package kob;

import daoInterface.GameDao;
import daoInterface.PlayerDao;
import daoInterface.ResultDao;
import daoJdbc.DatabaseConnection;
import daoJdbc.GameDaoJDBC;
import daoJdbc.PlayerDaoJDBC;
import daoJdbc.ResultDaoJDBC;
import dto.Game;
import dto.Player;
import dto.Result;

import java.io.IOException;
import java.sql.Date;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the main application class. It queries and utilizes the different objects from the model to display rankings statistics etc
 */
public class KOB {

    // TODO Move those settings to properties database.
    public final static int INITIAL_SCORE = 50;
    private ResultDao resultDao;
    private PlayerDao playerDao;
    public final static DecimalFormat DF = new DecimalFormat("0.0");
    private final static int RECENT_GAMES = 15;
    public final static boolean LIMIT_TO_A_YEAR = false;
    // Objects to access data.
    private GameDao gameDao;


    /**
     * Starts the system, and sets up data access. It also initializes the data if need be.
     */
    public KOB() {
        this.gameDao = new GameDaoJDBC();
        this.resultDao = new ResultDaoJDBC();
        this.playerDao = new PlayerDaoJDBC();
        try {
            this.initialize();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Press a key to get started");
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        KOB kob = new KOB();

        // Show some behaviours
        // Print the current ranking based on player scores.
        System.out.println(kob.getPrintableRanking());
        // Print stats on players.
        kob.printPlayerStats();
        // Print stats on the tiers (victory percentages,...)
        kob.printTierStats();

    }

    private void printTierStats() {
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
                    System.out.println(top.getName() + ": " + Math.toIntExact(count * 100 / nbGames) + "% (" + count + " victories in " + nbGames + " appearances)");
                }
                while (tmp.get(i).contains(top)) {
                    tmp.get(i).remove(top);
                }
            }

        }
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
    }

    private void printPlayerStats() {
        //Players with the most games
        int nbGames = gameDao.getAllCompleteGames().size();
        List<Player> mostRegularPlayers = new ArrayList<>();
        List<Player> leastRegularPlayers = new ArrayList<>();
        for (Player player : playerDao.getAllPlayers()) {
            if (mostRegularPlayers.isEmpty()) {
                mostRegularPlayers.add(player);
            } else {
                if (resultDao.getAllResultsFromPlayer(player).size() > resultDao.getAllResultsFromPlayer(mostRegularPlayers.get(0)).size()) {
                    mostRegularPlayers.clear();
                    mostRegularPlayers.add(player);
                } else if (resultDao.getAllResultsFromPlayer(player).size() == resultDao.getAllResultsFromPlayer(mostRegularPlayers.get(0)).size()) {
                    mostRegularPlayers.add(player);
                }
            }

            if (leastRegularPlayers.isEmpty()) {
                leastRegularPlayers.add(player);
            } else {
                if (resultDao.getAllResultsFromPlayer(player).size() < resultDao.getAllResultsFromPlayer(leastRegularPlayers.get(0)).size()) {
                    leastRegularPlayers.clear();
                    leastRegularPlayers.add(player);
                } else if (resultDao.getAllResultsFromPlayer(player).size() == resultDao.getAllResultsFromPlayer(leastRegularPlayers.get(0)).size()) {
                    leastRegularPlayers.add(player);
                }
            }
        }
        StringBuilder appearanceString = new StringBuilder();
        appearanceString.append("Most regular player")
                .append(mostRegularPlayers.size() > 1 ? "s (" : " (")
                .append(resultDao.getAllResultsFromPlayer(mostRegularPlayers.get(0)).size())
                .append(" out of ")
                .append(nbGames)
                .append(" games): \n");
        for (Player mostRegularPlayer : mostRegularPlayers) {
            appearanceString.append(mostRegularPlayer.getName()).append("\n");
        }
        appearanceString.append("\nLeast regular player").append(leastRegularPlayers.size() > 1 ? "s (" : " (").append(resultDao.getAllResultsFromPlayer(leastRegularPlayers.get(0)).size()).append(" games)").append(": \n");
        for (Player player : leastRegularPlayers) {
            appearanceString.append(player.getName()).append("\n");
        }
        System.out.println(appearanceString);
    }

    private void initialize() throws SQLException {

        // Check if initialization is required.
        Connection connection = DatabaseConnection.getInstance().getConnection();
        boolean initialized = false;
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT Value FROM Properties where name='kob.initializationCompleted'";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                initialized = rs.getString(1).equals("true");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        if (!initialized) {
            System.out.println("Initialization required...");
            java.util.Date start = new java.util.Date();

            for (Game game : gameDao.getAllCompleteGames()) {
                // Games are ordered by session dates.
                this.updateResultsFromGame(game);
            }
            try {
                // Mark the system as initialized.
                Statement stmt = connection.createStatement();
                String sql = "UPDATE Properties set Value='true' where name='kob.initializationCompleted'";
                stmt.executeUpdate(sql);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            DatabaseConnection.getInstance().getConnection().close();
            java.util.Date end = new java.util.Date();
            System.out.println("...Initialization done - " + ((double) (end.getTime() - start.getTime())) / 1000 + " seconds. ");
        } else {
            System.out.println("Ranking has already been initialized. ");
        }


    }

    /**
     * This method return the current players ranking, and generates a String to display it.
     *
     * @return A formatted string that shows the current player ranking.
     */
    public String getPrintableRanking() {
        return "Current Ranking: \n" + getPrintableRankingAtGame(gameDao.getLastCompletedGame());
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
        List<Player> ranking = playerDao.getAllPlayers();
        for (Player player : ranking) {
            player.setHasResults(false);
        }

        for (Player player : ranking) {
            setPlayerMasterScoreAtGame(game, player);
        }
        ranking = ranking.stream().filter(Player::isHasResults).sorted(Player::compare).collect(Collectors.toList());
        Collections.reverse(ranking);
        return ranking;
    }

    private double getPlayerResultAverageAtGame(Game game, Player player) {
        List<Result> allResultsFromPlayer = resultDao.getAllResultsFromPlayer(player);
        allResultsFromPlayer.add(new Result(-1, 0, INITIAL_SCORE, new Date(0), 0));
        return allResultsFromPlayer.stream().filter(result -> result.getDateForLight().before(game.getDate())).mapToDouble(Result::getScore).average().getAsDouble();
    }

    private void setPlayerMasterScoreAtGame(Game game, Player player) {
        Date asOfDate = game.getDate();
        Date cutOffDate = new Date(0);
        // The cut off date is made by removing 15 results from the list of results. That is assuming that the id and date are in the same order...
        Game cutOffgame = gameDao.getGame(game.getId() - RECENT_GAMES);
        if (cutOffgame != null) {
            cutOffDate = cutOffgame.getDate();
        }
        List<Result> allPlayerResults = resultDao.getAllResultsFromPlayer(player);
        Date finalCutOffDate = cutOffDate;
        List<Result> resultsBeforeCutOffDate = allPlayerResults.stream().filter(it -> (it.getDateForLight().before(finalCutOffDate) || it.getDateForLight().equals(finalCutOffDate)) && (it.getDateForLight().equals(asOfDate) || it.getDateForLight().before(asOfDate))).collect(Collectors.toList());
        List<Result> resultsAfterCutOffDate = allPlayerResults.stream().filter(it -> it.getDateForLight().after(finalCutOffDate) && (it.getDateForLight().equals(asOfDate) || it.getDateForLight().before(asOfDate))).collect(Collectors.toList());

        double newScore = KOB.INITIAL_SCORE;
        if (resultsAfterCutOffDate.size() > 0 && resultsBeforeCutOffDate.size() > 0) {
            double averageOld = resultsBeforeCutOffDate.stream().mapToDouble(Result::getScore).average().getAsDouble();
            double newScores = resultsAfterCutOffDate.stream().mapToDouble(Result::getScore).sum();
            newScore = (averageOld + newScores) / (resultsAfterCutOffDate.size() + 1);
            player.setHasResults(true);

        } else if (resultsAfterCutOffDate.size() > 0) {
            double newScores = resultsAfterCutOffDate.stream().mapToDouble(Result::getScore).sum();
            newScore = (INITIAL_SCORE + newScores) / (resultsAfterCutOffDate.size() + 1);
            player.setHasResults(true);
        } else if (resultsBeforeCutOffDate.size() > 0) {
            newScore = resultsBeforeCutOffDate.stream().mapToDouble(Result::getScore).average().getAsDouble();
            player.setHasResults(true);
        }
        player.setMasterScore(newScore);
    }

    /**
     * This method uses the results from a game to calculate each player score for the provided game.
     *
     * @param game The game that will be updated.
     */
    private void updateResultsFromGame(Game game) {
        if (game.isComplete()) {
            List<Result> allResultsFromGame = resultDao.getAllResultsFromGameOrderByOriginalMasterScore(game);
            int numberOfAttendees = allResultsFromGame.size();
            int factor = numberOfAttendees / 4;

            // To start, we need to know the player's master score at the start of the game.
            // TODO Check what happens when a new game is created, but dated before the most recent game... Probably some initialization needed.
            boolean refresh = false;
            for (Result result : allResultsFromGame) {
                if (result.getPlayerMasterScoreBeforeGame() == 0) {
                    // TODO This is where the problem is in the current ranking. A player score at the start of the game is not it's current MasterScore, but the average of all its scores.
                    result.setPlayerMasterScoreBeforeGame(getPlayerResultAverageAtGame(game, result.getPlayer()));
                }
            }
            allResultsFromGame.sort(Result::compare);
            // Top score is calculated based on the top 25% of the best available players + 10.
            // If the value is lower than the best player's score, we'll use that score instead.
            double topScore = Math.max(10 + (allResultsFromGame.subList(0, factor).stream().mapToDouble(Result::getPlayerMasterScoreBeforeGame).sum() / factor), allResultsFromGame.get(0).getPlayerMasterScoreBeforeGame());
            // Bottom score is calculated based on the bottom 25% of the best available players - 10.
            // If the value is lower than the best player's score, we'll use that score instead.
            double bottomScore = Math.min(-10 + (allResultsFromGame.subList(allResultsFromGame.size() - factor, allResultsFromGame.size()).stream().mapToDouble(Result::getPlayerMasterScoreBeforeGame).sum() / factor), allResultsFromGame.get(allResultsFromGame.size() - 1).getPlayerMasterScoreBeforeGame());
            game.setHighestPoint(topScore);
            game.setLowestPoint(bottomScore);
            gameDao.updateGame(game);
            for (Result result : allResultsFromGame) {
                // A players score is calculated using top and bottom scores, and the number of attendees.
                double score = topScore - ((result.getResult() - 1) * (topScore - bottomScore) / (numberOfAttendees - 1));
                result.setScore(score);
            }
            resultDao.updateResults(allResultsFromGame);
        } else {
            System.err.println("Game is not complete");
        }
    }
}
