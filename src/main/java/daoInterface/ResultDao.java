package daoInterface;

import dto.Game;
import dto.Player;
import dto.Result;

import java.util.List;

public interface ResultDao {
        Result getResult(int id);
        List<Result> getAllResults();

        Result insertResult(Result result);

        boolean updateResult(Result result);

        boolean updateResults(List<Result> results);

        boolean deleteResult(Result result);

        List<Result> getAllResultsFromGame(Game game);

        List<Result> getAllResultsFromGameOrderByOriginalMasterScore(Game game);

        List<Result> getAllResultsFromPlayer(Player p);
}
