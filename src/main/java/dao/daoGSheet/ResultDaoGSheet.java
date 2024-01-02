package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.ResultDao;
import dto.Game;
import dto.Player;
import dto.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ResultDaoGSheet implements ResultDao {
    private static final Logger log = LogManager.getLogger(ResultDaoGSheet.class);
    private List<Result> results;
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
            try {
                List<List<Object>> sheet = GSheetConnector.getResults();
                for (int i = 1; i < sheet.size(); i++) {
                    List<Object> objects = sheet.get(i);

                    for (int j = 2; j < objects.size(); j++) {
                        Object object = objects.get(j);
                        if (object != null && !object.toString().isEmpty()){
                            results.add(new Result(games.getGame(sheet.size()-i), players.getPlayerByName(sheet.get(0).get(j).toString()), Double.parseDouble(object.toString())));
                        }
                    }
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
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
        return results.stream().filter(a -> a.getSession().equals(game)).collect(Collectors.toList());
    }

    @Override
    public List<Result> getAllResultsFromPlayer(Player p) {
        // This needs to be improved drastically. 
        if (results == null) {
            this.getAllResults();
        }
        return results.stream().filter(a -> a.getPlayer().equals(p)).collect(Collectors.toList());
    }
}
