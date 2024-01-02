package dao.daoGSheet;

import dao.daoInterface.GameDao;
import dto.Game;
import kob.KOB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GameDaoGSheet implements GameDao {

    private static final Logger log = LogManager.getLogger(KOB.class);
    private List<Game> games;

    @Override
    public Game getGame(long id) {
        if (games == null) {
            getAllGames();
        }
        // Find the game where the ID matches
        return games.stream().filter(a -> a.getId() == id).findFirst().orElse(null);
    }

    @Override
    public List<Game> getAllGames() {
        if (this.games == null) {
            // First line is all players - we'll skip it but we'll use it

            log.debug("Getting all games from sheet. ");
            games = new ArrayList<>();
            try {
                // Good through each lines of the results sheets
                List<List<Object>> sheet = GSheetConnector.getResults();
                for (int j = 1; j < sheet.size(); j++) {
                    // First column is the game "id" - we use incremental instead...
                    // Second column is date
                    List<Object> game = sheet.get(j);
                    if (game.size() > 1) {
                        String date = game.get(1).toString();
                        // Specify the date pattern matching your Google Sheets date format
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        // Parse the string into a LocalDate object
                        LocalDate convertedLocalDate = LocalDate.parse(date, formatter);
                        // Add the game
                        games.add(new Game(sheet.size()-j, convertedLocalDate, 0, 0));
                    }
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            log.debug(" All games now loaded from sheet. " + games.size());
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
        long closestDate = Long.MAX_VALUE;
        for (Game currentGame : games) {
            if (asOfDate.toEpochDay() - currentGame.getDate().toEpochDay() < closestDate) {
                closest = currentGame;
            }
        }
        return closest;
    }
}
