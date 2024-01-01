package dao.daoInterface;

import dto.Game;
import dto.Player;
import dto.Result;

import java.util.List;

public interface ResultDao {
        List<Result> getAllResults();

        Result insertResult(Result result);

        List<Result> getAllResultsFromGame(Game game);

        List<Result> getAllResultsFromPlayer(Player p);
}
