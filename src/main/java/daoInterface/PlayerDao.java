package daoInterface;

import dto.Game;
import dto.Player;

import java.util.List;

public interface PlayerDao {
    Player getPlayer(long id);

    List<Player> getAllPlayers();

    boolean insertPlayer(Player player);

    boolean updatePlayer(Player player);

    boolean deletePlayer(Player player);

    List<Player> getPlayersRankings();

    boolean resetAllPlayersScore(int i);

    List<Player> getAllPlayersFromGame(Game game);

    Player getPlayerByName(String name);
}
