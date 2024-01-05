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
    private final PlayerDao players;
    private final GameDao games;

    public ResultDaoGSheet(GameDao gameDao, PlayerDao playerDao) {
        this.games = gameDao;
        this.players = playerDao;
    }

    @Override
    public List<Result> getAllResults() {
        if (results == null) {
            log.debug("Getting all results from sheet");
            results = new ArrayList<>();
            List<List<Object>> sheet = GSheetConnector.getResults();
            int gameCount=0;
            for (int i = 1; i < sheet.size(); i++) {
                List<Object> objects = sheet.get(i);
                // Need a minimum of results to count
                long count = objects.stream().filter(object -> object != null && !object.toString().isEmpty()).count()-2;
                if(count>KOB.MINIMUM_NB_PLAYERS){
                    for (int j = 2; j < objects.size(); j++) {
                        Object object = objects.get(j);
                        if (object != null && !object.toString().isEmpty()){
                            Result e = new Result(games.getGame(gameCount), players.getPlayerByName(sheet.get(0).get(j).toString()), Double.parseDouble(object.toString()));
                            if(e.getSession()!=null){
                                results.add(e);
                                List<Result> playersResult = resultsPerPlayer.getOrDefault(e.getPlayer(), new ArrayList<>());
                                playersResult.add(e);
                                resultsPerPlayer.put(e.getPlayer(), playersResult);
                                List<Result> gameResults = resultsPerGames.getOrDefault(e.getSession(), new ArrayList<>());
                                gameResults.add(e);
                                resultsPerGames.put(e.getSession(), gameResults);
                            }

                        }
                    }
                    gameCount++;
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
