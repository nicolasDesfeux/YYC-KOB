package dao.daoJdbc;

import dao.daoInterface.GameDao;
import dto.Game;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GameDaoJDBC implements GameDao {
    private static final Logger log = LogManager.getLogger(GameDaoJDBC.class);

    @Override
    public Game getGame(long id) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM game WHERE id=" + id);
            if (rs.next()) {
                return extractGameFromResultSet(rs);
            }
        } catch (SQLException ex) {
            log.error("Unable to retrieve the game for id: "+id, ex);
        }
        return null;
    }

    @Override
    public List<Game> getAllGames() {
        List<Game> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT * FROM game order by sessionDate asc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {
            log.error("Unable to retrieve the games", ex);
        }
        return results;
    }

    @Override
    public List<Game> getAllOpenGames() {
        List<Game> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT * FROM game where not(isComplete) order by sessionDate asc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {
            log.error("Unable to retrieve the open games", ex);
        }
        return results;
    }

    @Override
    public List<Game> getAllCompleteGames() {
        List<Game> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT * FROM game where isComplete order by sessionDate asc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {

            log.error("Unable to retrieve the completed games", ex);
        }
        return results;
    }


    @Override
    public Game insertGame(Game game) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO game (sessionDate, isComplete) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setDate(1, Date.valueOf(game.getDate()));
            ps.setBoolean(2, true);
            int i = ps.executeUpdate();
            if (i == 1) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        game.setId(generatedKeys.getLong(1));
                    } else {
                        throw new SQLException("Creating player failed, no ID obtained.");
                    }
                }
                return game;
            }
            } catch (SQLException ex) {

            log.error("Unable to insert new game: " + game, ex);
            }
        return null;
    }

    @Override
    public boolean updateGame(Game game) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE game SET sessionDate=?, highestPoint=?, lowestPoint=? WHERE id=?");
            ps.setDate(1, Date.valueOf(game.getDate()));
            ps.setDouble(2, game.getHighestPoint());
            ps.setDouble(3, game.getLowestPoint());
            ps.setLong(4, game.getId());
            int i = ps.executeUpdate();
            if(i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            log.error("Unable to update the game: "+game, ex);
        }
        return false;
    }

    @Override
    public boolean deleteGame(Game game) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            int i = stmt.executeUpdate("DELETE FROM game WHERE id=" + game.getId());
            if (i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            log.error("Unable to delete the game " + game, ex);
        }
        return false;
    }

    @Override
    public Game getLastCompletedGame() {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM game where isComplete order by sessionDate desc limit 1");
            if (rs.next()) {
                return extractGameFromResultSet(rs);
            }
        } catch (SQLException ex) {

            log.error("Unable to retrieve the last complete game", ex);
        }
        return null;
    }

    @Override
    public Game getGameClosestTo(LocalDate asOfDate) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM game where isComplete and sessionDate<=? order by sessionDate desc limit 1");
            ps.setDate(1, Date.valueOf(asOfDate));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractGameFromResultSet(rs);
            }
        } catch (SQLException ex) {

            log.error("Unable to retrieve the game closest to: "+asOfDate, ex);
        }
        return null;
    }


    private Game extractGameFromResultSet(ResultSet rs) throws SQLException {
        return new Game(rs.getLong("id"), rs.getDate("sessionDate").toLocalDate(), rs.getDouble("highestPoint"), rs.getDouble("lowestPoint"));
    }
}
