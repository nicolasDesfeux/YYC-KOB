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

import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.stream.Collectors;

public class KOB {

    private GameDao gameDao;
    private ResultDao resultDao;
    private PlayerDao playerDao;
    public final static int INITIAL_SCORE = 50;
    private final static int RECENT_GAMES = 15;
    public final static boolean LIMIT_TO_A_YEAR = false;

    public KOB() {
        this.gameDao = new GameDaoJDBC();
        this.resultDao = new ResultDaoJDBC();
        this.playerDao = new PlayerDaoJDBC();
    }

    public static void main(String[] args) throws Exception {

        KOB kob = new KOB();
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
        if(!initialized){
            System.out.println("Initialization required...");
            kob.initialize();
            System.out.println("...Initialization done");
        }else{
            System.out.println("Ranking has already been initialized. ");
        }

        System.out.println(kob.getPrintableRanking());
        /*System.out.println(kob.playerDao.getAllPlayers());*/

        /*Game newGame = new Game();
        kob.gameDao.insertGame(newGame);

        Map<String, Integer> results = new HashMap<>();
        List<Result> resultsToPersist = new ArrayList<>();
        results.put("Nicolas Desfeux", 3);
        results.put("Matty Jose", 4);
        results.put("Colton De Man", 1);
        results.put("Josh Woelfel", 2);
        results.put("Scott Lahey", 5);
        results.put("Phil Woelfel", 6);
        for (String name : results.keySet()) {
            Player p = kob.playerDao.getPlayerByName(name);
            if (p != null) {
                Result result = new Result(newGame, p, results.get(name));
                resultsToPersist.add(result);
            }else{
                System.err.println("Cannot find player with name: "+name);
            }
        }
        for (Result result : resultsToPersist) {
            kob.resultDao.insertResult(result);
        }
        kob.actualizeRankingAndResultsFromGame(newGame);
        System.out.println(kob.getPrintableRanking());*/

        //kob.printPlayerStats();
        //kob.printTierStats();
        //kob.checkAllGames();

        //Find which game does not have 1st places?
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
        System.out.println(tmp.get(0).size());

        for (int i = 0; i < tmp.size(); i = i + 4) {
            System.out.println("Tier " + (i / 4 + 1) + " statistics: ");
            int overallCount = 0;
            while (tmp.get(i).size() > 0) {
                Player top = mostCommon(tmp.get(i));
                int finalI = i;
                long nbGames = resultDao.getAllResultsFromPlayer(top).stream().filter(result -> result.getResult() >= (finalI + 1) && result.getResult() < (finalI + 5)).count();
                int count = 0;
                for (Player player : tmp.get(i)) {
                    if (player.equals(top)) {
                        count++;
                        overallCount++;
                    }
                }
                System.out.println(top.getName() + ": " + Math.toIntExact(count * 100 / nbGames) + "% (" + count + " victories in " + nbGames + " appearances)");
                while (tmp.get(i).contains(top)) {
                    tmp.get(i).remove(top);
                }
            }
            System.out.println("Overall count: " + overallCount);

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
        int nbGames = gameDao.getAllOpenGames().size();
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
        java.util.Date start = new java.util.Date();
        playerDao.resetAllPlayersScore(INITIAL_SCORE);

        GameDao gameDao = new GameDaoJDBC();

        for (Game game : gameDao.getAllCompleteGames()) {
            this.actualizeRankingAndResultsFromGame(game);
        }
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "UPDATE Properties set Value='true' where name='kob.initializationCompleted'";
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        DatabaseConnection.getInstance().getConnection().close();
        java.util.Date end = new java.util.Date();
        System.out.println("Initialization - " + (end.getTime() - start.getTime()) / 1000 + " seconds. ");

    }

    private void actualizeRankingAndResultsFromGame(Game game) throws SQLException {
        this.updateResultsFromGame(game);
        this.getPlayersRankingAtGame(game, true);
    }

    private List<Player> getPlayersRankingAtGame(Game game) throws SQLException {
        return getPlayersRankingAtGame(game, false);
    }

    public String getPrintableRanking() {
        List<Player> rankings = playerDao.getPlayersRankings();
        StringBuilder rankingString = new StringBuilder();
        for (int i = 0; i < rankings.size(); i++) {
            rankingString.append(i + 1).append(". ").append(rankings.get(i).getName()).append(" (").append(rankings.get(i).getMasterScore()).append(")\n");
        }
        return "Current Ranking: \n" + rankingString;
    }

    private List<Player> getPlayersRankingAtGame(Game game, boolean persist) {
        GameDao gameDao = new GameDaoJDBC();
        List<Player> ranking = playerDao.getAllPlayers();
        for (Player player : ranking) {
            player.setHasResults(false);
        }
        Date asOfdate = game.getDate();
        Date cutOffDate = new Date(0);
        // The cut off date is made by removing 15 results from the list of results. That is assuming that the id and date are in the same order...
        Game cutOffgame = gameDao.getGame(game.getId() - RECENT_GAMES);
        if (cutOffgame != null) {
            cutOffDate = cutOffgame.getDate();
        }


        for (Player player : ranking) {
            List<Result> allPlayerResults = resultDao.getAllResultsFromPlayer(player);
            Date finalCutOffDate = cutOffDate;
            List<Result> resultsBeforeCutOffDate = allPlayerResults.stream().filter(it -> (it.getDateForLight().before(finalCutOffDate) || it.getDateForLight().equals(finalCutOffDate)) && (it.getDateForLight().equals(asOfdate) || it.getDateForLight().before(asOfdate))).collect(Collectors.toList());
            List<Result> resultsAfterCutOffDate = allPlayerResults.stream().filter(it -> it.getDateForLight().after(finalCutOffDate) && (it.getDateForLight().equals(asOfdate) || it.getDateForLight().before(asOfdate))).collect(Collectors.toList());

            if (resultsAfterCutOffDate.size() > 0 && resultsBeforeCutOffDate.size() > 0) {
                double averageOld = resultsBeforeCutOffDate.stream().mapToDouble(Result::getScore).average().getAsDouble();
                double newScores = resultsAfterCutOffDate.stream().mapToDouble(Result::getScore).sum();
                double newScore = (averageOld + newScores) / (resultsAfterCutOffDate.size() + 1);
                player.setMasterScore(newScore);
                player.setHasResults(true);
                if (persist) {
                    playerDao.updatePlayer(player);
                }
            } else if (resultsAfterCutOffDate.size() > 0) {
                double newScores = resultsAfterCutOffDate.stream().mapToDouble(Result::getScore).sum();
                double newScore = (INITIAL_SCORE + newScores) / (resultsAfterCutOffDate.size() + 1);
                player.setMasterScore(newScore);
                player.setHasResults(true);
                if (persist) {
                    playerDao.updatePlayer(player);
                }
            } else if (resultsBeforeCutOffDate.size() > 0) {
                double averageOld = resultsBeforeCutOffDate.stream().mapToDouble(Result::getScore).average().getAsDouble();
                player.setMasterScore(averageOld);
                player.setHasResults(true);
                if (persist) {
                    playerDao.updatePlayer(player);
                }
            }
        }
        ranking = ranking.stream().filter(Player::isHasResults).sorted(Player::compare).collect(Collectors.toList());
        Collections.reverse(ranking);

        return ranking;

    }

    private void updateResultsFromGame(Game game) {
        List<Result> allResultsFromGame = resultDao.getAllResultsFromGame(game);
        List<Player> players = playerDao.getAllPlayersFromGame(game);
        int numberOfAttendees = allResultsFromGame.size();
        int factor = numberOfAttendees / 4;

        double topScore = Math.max(10 + (players.subList(0, factor).stream().mapToDouble(Player::getMasterScore).sum() / factor), players.stream().max(Player::compare).get().getMasterScore());
        double bottomScore = Math.min(-10 + (players.subList(players.size() - factor, players.size()).stream().mapToDouble(Player::getMasterScore).sum() / factor), players.stream().min(Player::compare).get().getMasterScore());
        game.setHighestPoint(topScore);
        game.setLowestPoint(bottomScore);
        gameDao.updateGame(game);
        for (Result result : allResultsFromGame) {
            double score = topScore - ((result.getResult() - 1) * (topScore - bottomScore) / (numberOfAttendees - 1));
            result.setScore(score);
            resultDao.updateResult(result);
        }
    }
}
