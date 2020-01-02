package daoJdbc;

import daoInterface.GameDao;
import dto.Game;
import kob.KOB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GameDaoJDBC implements GameDao {
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
            ex.printStackTrace();
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
            if(KOB.LIMIT_TO_A_YEAR){
                sql="SELECT * FROM game where game.sessionDate >= DATE_SUB(NOW(),INTERVAL 1 YEAR) order by sessionDate asc;";
            }
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
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
            if(KOB.LIMIT_TO_A_YEAR){
                sql="SELECT * FROM game where not(isComplete) and game.sessionDate >= DATE_SUB(NOW(),INTERVAL 1 YEAR) order by sessionDate asc;";
            }
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
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
            if(KOB.LIMIT_TO_A_YEAR){
                sql="SELECT * FROM game where isComplete and game.sessionDate >= DATE_SUB(NOW(),INTERVAL 1 YEAR) order by sessionDate asc;";
            }
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractGameFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }


    @Override
    public boolean insertGame(Game game) {
            Connection connection = DatabaseConnection.getInstance().getConnection();
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO game (sessionDate) VALUES (?)");
                ps.setDate(1, game.getDate());
                int i = ps.executeUpdate();
                if(i == 1) {
                    return true;
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
    }

    @Override
    public boolean updateGame(Game game) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE game SET sessionDate=?, highestPoint=?, lowestPoint=? WHERE id=?");
            ps.setDate(1, game.getDate());
            ps.setDouble(2, game.getHighestPoint());
            ps.setDouble(3, game.getLowestPoint());
            ps.setLong(4, game.getId());
            int i = ps.executeUpdate();
            if(i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
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
            ex.printStackTrace();
        }
        return false;
    }

    @Override
    public Game getLastCompletedGame() {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM game order by sessionDate desc limit 1");
            if (rs.next()) {
                return extractGameFromResultSet(rs);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }


    private Game extractGameFromResultSet(ResultSet rs) throws SQLException {
        return new Game(rs.getLong("id"), rs.getDate("sessionDate"), rs.getDouble("highestPoint"), rs.getDouble("lowestPoint"), rs.getBoolean("isComplete"));
    }
}
