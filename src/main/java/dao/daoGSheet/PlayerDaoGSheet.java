package dao.daoGSheet;

import dao.daoInterface.PlayerDao;
import dto.Player;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerDaoGSheet implements PlayerDao {

    List<Player> players;

    @Override
    public Player getPlayer(long id) {
        return null;
    }

    @Override
    public List<Player> getAllPlayers() {
        if(players==null){
            players = new ArrayList<>();
            try {
                List<List<Object>> sheet = GSheetConnector.getResults();
                List<Object> names = sheet.get(0);
                for (Object name : names) {
                    players.add(new Player(name.toString()));
                }

            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        return players;
    }

    @Override
    public Player insertPlayer(Player player) {
        return null;
    }

    @Override
    public void updatePlayer(Player player) {
    }


    @Override
    public Player getPlayerByName(String name) {
        getAllPlayers();
        Optional<Player> player = players.stream().filter(a -> a.getName().equals(name)).findFirst();
        return player.orElse(null);
    }
}