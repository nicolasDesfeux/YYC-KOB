package dao.daoGSheet;

import dao.daoInterface.PlayerDao;
import dto.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerDaoGSheet implements PlayerDao {

    private static final Logger log = LogManager.getLogger(PlayerDaoGSheet.class);

    private final GSheetConnector connector;
    private List<Player> players;
    /** Canonical name -> player, so lookups tolerate case and spacing differences. */
    private Map<String, Player> byNormalisedName;

    public PlayerDaoGSheet(GSheetConnector connector) {
        this.connector = connector;
    }

    @Override
    public Player getPlayer(long id) {
        return null;
    }

    @Override
    public List<Player> getAllPlayers() {
        if (players == null) {
            players = new ArrayList<>();
            byNormalisedName = new HashMap<>();
            List<List<Object>> sheet = connector.getResults();
            List<Object> names = sheet.get(0);
            for (int i = 2; i < names.size(); i++) {
                String raw = names.get(i) == null ? "" : names.get(i).toString().trim();
                if (raw.isEmpty()) continue;   // ignore padding columns in the header

                Player player = new Player(raw);
                String key = player.getNormalisedName();
                Player existing = byNormalisedName.putIfAbsent(key, player);
                if (existing != null) {
                    log.warn("Duplicate player column '{}' (already seen as '{}') — ignoring the later one",
                            raw, existing.getName());
                    continue;
                }
                players.add(player);
            }
            log.debug("Loaded {} players from the sheet header", players.size());
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
        return byNormalisedName.get(Player.normaliseName(name));
    }
}
