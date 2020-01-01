package daoJdbc;

import daoInterface.PlayerDao;
import dto.Game;
import dto.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlayerDaoJDBC implements PlayerDao {
    @Override
    public Player getPlayer(long id) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM player WHERE id=" + id);
            if (rs.next()) {
                return extractPlayerFromResultSet(rs);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Player> getAllPlayers() {
        List<Player> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM player");
            while (rs.next()) {
                results.add(extractPlayerFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }

    @Override
    public boolean insertPlayer(Player player) {
            Connection connection = DatabaseConnection.getInstance().getConnection();
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO player (name, masterScore) VALUES (?, ?)");
                ps.setString(1, player.getName());
                ps.setDouble(2, player.getMasterScore());
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
    public boolean updatePlayer(Player player) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE player SET name=?, masterScore=?, hasScore=? WHERE id=?");
            ps.setString(1, player.getName());
            ps.setDouble(2, player.getMasterScore());
            ps.setBoolean(3,player.isHasResults());
            ps.setLong(4, player.getId());
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
    public boolean deletePlayer(Player player) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            int i = stmt.executeUpdate("DELETE FROM player WHERE id=" + player.getId());
            if(i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    @Override
    public List<Player> getPlayersRankings() {
        List<Player> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM player where hasScore=1 order by masterScore desc");
            while (rs.next()) {
                results.add(extractPlayerFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }

    @Override
    public List<Player> getAllPlayersFromGame(Game game) {
        List<Player> results = new ArrayList<>();
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM player where id in (select playerId from result where sessionId="+game.getId()+") order by masterScore desc");
            while (rs.next()) {
                results.add(extractPlayerFromResultSet(rs));
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return results;
    }

    @Override
    public Player getPlayerByName(String name) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM player WHERE name=?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractPlayerFromResultSet(rs);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean resetAllPlayersScore(int score) {
        Connection connection = DatabaseConnection.getInstance().getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE player SET masterScore=?, hasScore=0");
            ps.setLong(1, score);
            int i = ps.executeUpdate();
            if(i == 1) {
                return true;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }


    private Player extractPlayerFromResultSet(ResultSet rs) throws SQLException {
        return new Player(rs.getLong("id"), rs.getString("name"), rs.getDouble("masterScore"), rs.getBoolean("hasScore"));
    }
}
