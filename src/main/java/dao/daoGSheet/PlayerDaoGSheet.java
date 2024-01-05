package dao.daoGSheet;

import dao.daoInterface.PlayerDao;
import dto.Player;

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
            List<List<Object>> sheet = GSheetConnector.getResults();
            List<Object> names = sheet.get(0);
            for (int i = 2; i < names.size(); i++) {
                Object name = names.get(i);
                players.add(new Player(name.toString()));
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
