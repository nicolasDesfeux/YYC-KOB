package daoJdbc;

import daoInterface.GameDao;
import daoInterface.PlayerDao;
import daoInterface.ResultDao;
import dto.Game;
import dto.Player;
import dto.Result;
import kob.KOB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ResultDaoJDBC implements ResultDao {
    @Override
    public Result getResult(int id) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM result WHERE id=" + id);
            if (rs.next()) {
                return extractResultFromResultSet(rs);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

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
    public boolean insertResult(Result result) {
            Connection connection = DatabaseConnection.getInstance().getConnection();
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO result (playerId, sessionId, result, score) VALUES (?, ?, ?, ?)");
                ps.setLong(1, result.getPlayer().getId());
                ps.setLong(2, result.getSession().getId());
                ps.setLong(3, result.getResult());
                ps.setDouble(4, result.getScore());
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
    public boolean updateResult(Result result) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE result SET playerId=?, sessionId=?, result=?, score=?, playerMasterScoreBeforeGame=? WHERE id=?");
            ps.setLong(1, result.getPlayer().getId());
            ps.setLong(2, result.getSession().getId());
            ps.setLong(3, result.getResult());
            ps.setDouble(4, result.getScore());
            ps.setDouble(5, result.getPlayerMasterScoreBeforeGame());
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

    @Override
    public boolean updateResults(List<Result> results) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE result SET playerId=?, sessionId=?, result=?, score=?, playerMasterScoreBeforeGame=? WHERE id=?");
            for (Result result : results) {
                ps.setLong(1, result.getPlayer().getId());
                ps.setLong(2, result.getSession().getId());
                ps.setLong(3, result.getResult());
                ps.setDouble(4, result.getScore());
                ps.setDouble(5, result.getPlayerMasterScoreBeforeGame());
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
    public boolean deleteResult(Result result) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            int i = stmt.executeUpdate("DELETE FROM result WHERE id=" + result.getId());
            if(i == 1) {
                return true;
            }
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
            ResultSet rs = stmt.executeQuery("SELECT * FROM result where sessionId=" + game.getId() + " ORDER BY result");
            while (rs.next()) {
                results.add(extractResultFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }

    @Override
    public List<Result> getAllResultsFromGameOrderByOriginalMasterScore(Game game) {
        List<Result> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM result where sessionId=" + game.getId() + " ORDER BY playerMasterScoreBeforeGame desc");
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
            if(KOB.LIMIT_TO_A_YEAR){
                sql+=" and game.sessionDate >= DATE_SUB(NOW(),INTERVAL 1 YEAR);";
            }
            sql += " order by game.sessionDate desc";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                Result res = extractResultLightFromResultSet(rs);
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
        return new Result(rs.getLong("id"), game.getGame(rs.getLong("sessionId")), player.getPlayer(rs.getLong("playerId")), rs.getLong("result"), rs.getDouble("score"), rs.getDouble("playerMasterScoreBeforeGame"));
    }

    private Result extractResultLightFromResultSet(ResultSet rs) throws SQLException {
        return new Result(rs.getLong("id"), rs.getLong("result"), rs.getDouble("score"), rs.getDate("sessionDate"), rs.getDouble("playerMasterScoreBeforeGame"));
    }
}
