package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.ResultDao;
import dto.Game;
import dto.Player;
import dto.Result;
import kob.KOB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultDaoGSheet implements ResultDao {
    private static final Logger log = LogManager.getLogger(ResultDaoGSheet.class);
    private List<Result> results;
    private final Map<Player, List<Result>> resultsPerPlayer = new HashMap<>();
    private final Map<Game, List<Result>> resultsPerGames = new HashMap<>();
    private final GSheetConnector connector;
    private final PlayerDao players;
    private final GameDao games;

    public ResultDaoGSheet(GSheetConnector connector, GameDao gameDao, PlayerDao playerDao) {
        this.connector = connector;
        this.games = gameDao;
        this.players = playerDao;
    }

    @Override
    public List<Result> getAllResults() {
        if (results == null) {
            log.debug("Getting all results from sheet");
            results = new ArrayList<>();
            // Use the authoritative game list from GameDao so IDs are guaranteed to match.
            List<Game> allGames = games.getAllGames();
            List<List<Object>> sheet = connector.getResults();
            int gameIndex = 0;
            for (int i = 1; i < sheet.size() && gameIndex < allGames.size(); i++) {
                List<Object> objects = sheet.get(i);
                long count = objects.stream().filter(object -> object != null && !object.toString().isEmpty()).count() - 2;
                if (count > KOB.MINIMUM_NB_PLAYERS) {
                    Game currentGame = allGames.get(gameIndex++);
for (int j = 2; j < sheet.get(0).size(); j++) {
                        Object object = j < objects.size() ? objects.get(j) : null;
                        if (object != null && !object.toString().isEmpty()) {
                            String playerName = sheet.get(0).get(j).toString();
                            Player player = players.getPlayerByName(playerName);
                            if (player == null) {
                                log.warn("Game {}: player '{}' (column {}) not found in player list — skipping result", currentGame.getId(), playerName, j);
                                continue;
                            }
                            double score;
                            try {
                                score = Double.parseDouble(object.toString());
                            } catch (NumberFormatException ex) {
                                log.warn("Game {}: unparseable result '{}' for player '{}' — skipping", currentGame.getId(), object, playerName);
                                continue;
                            }
                            Result e = new Result(currentGame, player, score);
                            results.add(e);
                            resultsPerPlayer.computeIfAbsent(e.getPlayer(), k -> new ArrayList<>()).add(e);
                            resultsPerGames.computeIfAbsent(e.getSession(), k -> new ArrayList<>()).add(e);
                        }
                    }
                }
            }
            log.debug("All results now loaded");
        }
        return results;
    }

    @Override
    public Result insertResult(Result result) {
        return null;
    }

    @Override
    public List<Result> getAllResultsFromGame(Game game) {
        if (results == null) {
            this.getAllResults();
        }
        return resultsPerGames.get(game);
    }

    @Override
    public List<Result> getAllResultsFromPlayer(Player p) {
        if (results == null) {
            this.getAllResults();
        }
        return resultsPerPlayer.get(p);
    }
}
