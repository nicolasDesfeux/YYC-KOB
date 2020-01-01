package daoInterface;

import dto.Game;
import dto.Player;
import dto.Result;

import java.util.List;
import java.util.Set;

public interface ResultDao {
        Result getResult(int id);
        List<Result> getAllResults();
        boolean insertResult(Result result);
        boolean updateResult(Result result);
        boolean deleteResult(Result result);

        List<Result> getAllResultsFromGame(Game game);

        List<Result> getAllResultsFromPlayer(Player p);
}
