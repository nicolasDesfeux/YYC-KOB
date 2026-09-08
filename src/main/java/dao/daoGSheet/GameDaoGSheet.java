package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dto.Game;
import kob.KOB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class GameDaoGSheet implements GameDao {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
                if (count > KOB.config().minimumNbPlayers) {
                    // A malformed row must not take down an unattended run: warn,
                    // skip it, and let the rest of the history load.
                    String rawId = row.get(0) == null ? "" : row.get(0).toString().trim();
                    String rawDate = row.size() > 1 && row.get(1) != null ? row.get(1).toString().trim() : "";
                    try {
                        long gameId = Long.parseLong(rawId);
                        LocalDate parsed = LocalDate.parse(rawDate, DATE_FORMAT);
                        games.add(new Game(gameId, parsed, 0, 0));
                    } catch (NumberFormatException e) {
                        log.warn("Row {}: game ID '{}' is not a number — skipping", j + 1, rawId);
                    } catch (DateTimeParseException e) {
                        log.warn("Game {} has no usable date (found '{}') — skipping until it is dated",
                                rawId, rawDate);
                    }
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
