package daoInterface;

import dto.Player;

import java.util.List;

public interface PlayerDao {
    Player getPlayer(long id);

    List<Player> getAllPlayers();

    boolean insertPlayer(Player player);

    boolean updatePlayer(Player player);

    boolean deletePlayer(Player player);

    Player getPlayerByName(String name);
}
