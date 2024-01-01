package dao.daoInterface;

import dto.Game;

import java.time.LocalDate;
import java.util.List;

public interface GameDao {
    Game getGame(long id);

    List<Game> getAllGames();

    List<Game> getAllOpenGames();

    List<Game> getAllCompleteGames();

    Game insertGame(Game game);

    boolean updateGame(Game game);

    boolean deleteGame(Game game);

    Game getLastCompletedGame();

    Game getGameClosestTo(LocalDate date);
}
