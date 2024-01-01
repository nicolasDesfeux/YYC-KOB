package dao.daoInterface;

import dto.Player;

import java.util.List;

public interface PlayerDao {
    Player getPlayer(long id);

    List<Player> getAllPlayers();

    Player insertPlayer(Player player);

    void updatePlayer(Player player);


    Player getPlayerByName(String name);
}
