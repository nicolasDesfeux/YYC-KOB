package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dto.Game;
import kob.KOB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GameDaoGSheet implements GameDao {

    private static final Logger log = LogManager.getLogger(GameDaoGSheet.class);
    private final GSheetConnector connector;
    private List<Game> games;

    public GameDaoGSheet(GSheetConnector connector) {
        this.connector = connector;
    }

    @Override
    public Game getGame(long id) {
        if (games == null) {
            getAllGames();
        }
        return games.stream().filter(a -> a.getId() == id).findFirst().orElse(null);
    }

    @Override
    public List<Game> getAllGames() {
        if (this.games == null) {
            log.debug("Getting all games from sheet.");
            games = new ArrayList<>();
            List<List<Object>> sheet = connector.getResults();
            for (int j = 1; j < sheet.size(); j++) {
                List<Object> row = sheet.get(j);
                long count = row.stream().filter(object -> object != null && !object.toString().isEmpty()).count() - 2;
                if (count > KOB.MINIMUM_NB_PLAYERS) {
                    long gameId = Long.parseLong(row.get(0).toString());
                    String date = row.get(1).toString();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    LocalDate convertedLocalDate = LocalDate.parse(date, formatter);
                    games.add(new Game(gameId, convertedLocalDate, 0, 0));
                }
            }
            log.debug("All games now loaded from sheet: " + games.size());
        }
        return this.games;
    }

    @Override
    public List<Game> getAllOpenGames() {
        return getAllGames();
    }

    @Override
    public List<Game> getAllCompleteGames() {
        return getAllGames();
    }

    @Override
    public Game insertGame(Game game) {
        return null;
    }

    @Override
    public boolean updateGame(Game game) {
        return false;
    }

    @Override
    public boolean deleteGame(Game game) {
        return false;
    }

    @Override
    public Game getLastCompletedGame() {
        if (games == null) {
            getAllGames();
        }
        return games.get(games.size() - 1);
    }

    @Override
    public Game getGameClosestTo(LocalDate asOfDate) {
        if (games == null) {
            getAllGames();
        }
        Game closest = null;
        long closestDiff = Long.MAX_VALUE;
        for (Game currentGame : games) {
            long diff = asOfDate.toEpochDay() - currentGame.getDate().toEpochDay();
            if (diff >= 0 && diff < closestDiff) {
                closestDiff = diff;
                closest = currentGame;
            }
        }
        return closest;
    }
}
