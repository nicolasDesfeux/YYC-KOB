package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dao.daoInterface.PlayerDao;
import dao.daoInterface.ResultDao;
import dto.Game;
import dto.Player;
import dto.Result;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ResultDaoGSheet implements ResultDao {

    private List<Result> results;

    @Override
    public List<Result> getAllResults() {
        if (results == null) {
            PlayerDao players = new PlayerDaoGSheet();
            GameDao games = new GameDaoGSheet();
            results = new ArrayList<>();
            try {
                List<List<Object>> sheet = GSheetConnector.getResults();
                for (int i = 1; i < sheet.size(); i++) {
                    List<Object> objects = sheet.get(i);

                    for (int j = 2; j < objects.size(); j++) {
                        Object object = objects.get(j);
                        if (object != null && !object.toString().isEmpty()){
                            results.add(new Result(games.getGame(i-1), players.getPlayerByName(sheet.get(0).get(j).toString()), Double.parseDouble(object.toString())));
                        }
                    }
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
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
        List<Result> resultsFromGame = new ArrayList<>();
        for (Result result : results) {
            if (result.getSession()!=null && result.getSession().getId() == game.getId()) {
                resultsFromGame.add(result);
            }
        }

        return resultsFromGame;
    }

    @Override
    public List<Result> getAllResultsFromPlayer(Player p) {
        if (results == null) {
            this.getAllResults();
        }
        List<Result> resultsFromgame = new ArrayList<>();
        for (Result result : results) {
            if (Objects.equals(result.getPlayer().getName(), p.getName())) {
                resultsFromgame.add(result);
            }
        }

        return resultsFromgame;
    }
}
