package dao.daoJdbc;

import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.ResultDao;
import dto.Game;
import dto.Player;
import dto.Result;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ResultDaoJDBC implements ResultDao {

    @Override
    public List<Result> getAllResults() {
        List<Result> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM result");
            while (rs.next()) {
                results.add(extractResultFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }

    @Override
    public Result insertResult(Result result) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO result (playerId, sessionId, result, score) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, result.getPlayer().getId());
            ps.setLong(2, result.getSession().getId());
            ps.setDouble(3, result.getResult());
            ps.setDouble(4, result.getScore());
            int i = ps.executeUpdate();
            if (i == 1) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        result.setId(generatedKeys.getLong(1));
                    } else {
                        throw new SQLException("Creating player failed, no ID obtained.");
                    }
                }
                return result;
            }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        return null;
    }

    public boolean updateResult(Result result) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE result SET playerId=?, sessionId=?, result=?, score=?, playerMasterScoreBeforeGame=? WHERE id=?");
            ps.setLong(1, result.getPlayer().getId());
            ps.setLong(2, result.getSession().getId());
            ps.setDouble(3, result.getResult());
            ps.setDouble(4, result.getScore());
            ps.setDouble(5, result.getPlayerMasterScoreBeforeGame().doubleValue());
            ps.setLong(6, result.getId());
            int i = ps.executeUpdate();
            if (i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }


    public boolean updateResults(List<Result> results) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE result SET playerId=?, sessionId=?, result=?, score=?, playerMasterScoreBeforeGame=? WHERE id=?");
            for (Result result : results) {
                ps.setLong(1, result.getPlayer().getId());
                ps.setLong(2, result.getSession().getId());
                ps.setDouble(3, result.getResult());
                ps.setDouble(4, result.getScore());
                ps.setDouble(5, result.getPlayerMasterScoreBeforeGame().doubleValue());
                ps.setLong(6, result.getId());
                ps.addBatch();
                ps.clearParameters();
            }

            int[] stmtReturn = ps.executeBatch();
            for (int i : stmtReturn) {
                if (i != 1)
                    return false;
            }
            return true;

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }


    @Override
    public List<Result> getAllResultsFromGame(Game game) {
        List<Result> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT * FROM result where sessionId=" + game.getId();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                results.add(extractResultFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }


    @Override
    public List<Result> getAllResultsFromPlayer(Player p) {
        List<Result> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            String sql = "SELECT * FROM result left join game on game.id=result.sessionId where playerId=" + p.getId();
            sql += " order by game.sessionDate desc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Result res = extractResultFromResultSet(rs);
                res.setPlayer(p);
                results.add(res);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }


    private Result extractResultFromResultSet(ResultSet rs) throws SQLException {
        PlayerDao player = new PlayerDaoJDBC();
        GameDao game = new GameDaoJDBC();
        return new Result(rs.getLong("id"), game.getGame(rs.getLong("sessionId")), player.getPlayer(rs.getLong("playerId")), rs.getLong("result"), rs.getDouble("score"), rs.getBigDecimal("playerMasterScoreBeforeGame"));
    }

}
