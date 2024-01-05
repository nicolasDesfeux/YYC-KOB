package dao.daoJdbc;

import java.sql.*;


public class DatabaseConnection {

    private static DatabaseConnection instance;
    private final Connection connection;
    private static final String URL = "jdbc:mysql://localhost:3306/kingOfTheBeach?useLegacyDatetimeCode=false&serverTimezone=UTC";
    private static final String USER = "kobApplication";
    private static final String PASS = "kob2019";

    private DatabaseConnection() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASS);
    }

    public Connection getConnection() {
        return connection;
    }

    public static DatabaseConnection getInstance() {
        try {
            if (instance == null) {
                instance = new DatabaseConnection();
                /*Connection connection = DatabaseConnection.getInstance().getConnection();
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
                        updateResultsFromGame(game);
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
                }*/
            } else if (instance.getConnection().isClosed()) {
                instance = new DatabaseConnection();
            }
        } catch (SQLException e) {
            System.err.println("Database Connection Creation Failed : " + e.getMessage());
        }
        return instance;
    }
    /*
    /**
     * This method uses the results from a game to calculate each player score for the provided game.
     *
     * @param game The game that will be updated.
     */
    /*private static void updateResultsFromGame(Game game) {
        if (game.isComplete()) {
            List<Result> allResultsFromGame = resultDao.getAllResultsFromGameOrderByOriginalMasterScore(game);
            int numberOfAttendees = allResultsFromGame.size();
            int factor = numberOfAttendees / 4;

            // To start, we need to know the player's master score at the start of the game.
            // TODO Check what happens when a new game is created, but dated before the most recent game... Probably some initialization needed.
            for (Result result : allResultsFromGame) {
                if (result.getPlayerMasterScoreBeforeGame() == 0) {
                    // TODO This is where the problem is in the current ranking. A player score at the start of the game is not it's current MasterScore, but the average of all its scores.
                    result.setPlayerMasterScoreBeforeGame(getPlayerResultAverageAtDate(game.getDate(), result.getPlayer()));
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

    private static double getPlayerResultAverageAtDate(LocalDate date, Player player) {
        List<Result> allResultsFromPlayer = resultDao.getAllResultsFromPlayer(player);
        if (KOB.LIMIT_TO_A_YEAR) {
            allResultsFromPlayer = allResultsFromPlayer.stream().filter(result -> result.getSession().getDate().isAfter(LocalDate.now().minusYears(1))).collect(Collectors.toList());
        }
        allResultsFromPlayer.add(new Result(-1, 0, KOB.INITIAL_SCORE, LocalDate.now(), 0));
        return allResultsFromPlayer.stream().filter(result -> result.getSession().getDate().isBefore(date)).mapToDouble(Result::getScore).average().getAsDouble();
    }*/
}