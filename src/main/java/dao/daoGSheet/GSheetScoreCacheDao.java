package dao.daoGSheet;

import dao.daoInterface.ScoreCacheDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the "Computed Scores" Google Sheet.
 *
 * Sheet format (3 columns):
 *   Game ID | Player | Score
 *
 * One row per (game, player) pair, sorted by game ID then player name.
 * Manual edits to any row are respected on the next run.
 */
public class GSheetScoreCacheDao implements ScoreCacheDao {

    private static final Logger log = LogManager.getLogger(GSheetScoreCacheDao.class);
    private static final String SHEET = "Computed Scores";
    private static final String RANGE = "'" + SHEET + "'!A1:C500000";

    private final GSheetConnector connector;

    public GSheetScoreCacheDao(GSheetConnector connector) {
        this.connector = connector;
    }

    @Override
    public Map<Long, Map<String, Double>> load() {
        Map<Long, Map<String, Double>> result = new LinkedHashMap<>();
        List<List<Object>> rows = connector.readRange(RANGE);
        if (rows == null || rows.size() <= 1) return result; // empty or header only

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 3) continue;
            try {
                long gameId = Long.parseLong(row.get(0).toString().trim());
                String player = row.get(1).toString().trim();
                double score = Double.parseDouble(row.get(2).toString().trim());
                result.computeIfAbsent(gameId, k -> new HashMap<>()).put(player, score);
            } catch (NumberFormatException e) {
                log.warn("Skipping malformed score cache row {}: {}", i, row);
            }
        }
        log.info("Loaded score cache: {} games, {} total entries",
                result.size(), result.values().stream().mapToInt(Map::size).sum());
        return result;
    }

    /**
     * Reads Sheet8 (player-per-row, game-per-column format) and returns scores
     * filtered per-player: leading entries equal to 50.0 are discarded until the
     * score first deviates from the initial value.
     *
     * Expected Sheet8 layout:
     *   Row 1 (header): Rank | Players | Calculated Master Score | Initial M Score | After G294 | After G295 | ...
     *   Row 2+:         rank | playerName | currentScore          | 50              | score      | score      | ...
     *
     * Game IDs are parsed from header values matching "After G{id}".
     * Columns 0–3 (Rank, Players, Calculated Master Score, Initial M Score) are skipped.
     */
    public Map<Long, Map<String, Double>> loadFromSheet8() {
        List<List<Object>> rows = connector.readRange("Sheet8!A1:ZZ1000");
        if (rows == null || rows.size() < 2) {
            log.warn("Sheet8 is empty or missing");
            return Collections.emptyMap();
        }

        // Parse header row: find columns whose header matches "After G{id}"
        List<Object> header = rows.get(0);
        // col → gameId for score columns
        Map<Integer, Long> colToGameId = new LinkedHashMap<>();
        for (int col = 0; col < header.size(); col++) {
            String h = header.get(col) == null ? "" : header.get(col).toString().trim();
            if (h.startsWith("After G")) {
                try {
                    long gameId = Long.parseLong(h.substring("After G".length()).trim());
                    colToGameId.put(col, gameId);
                } catch (NumberFormatException e) {
                    log.warn("Sheet8: could not parse game ID from header '{}' at col {}", h, col);
                }
            }
        }
        log.info("Sheet8: found {} game columns: {}", colToGameId.size(), colToGameId.values());

        // Collect per-player score sequences (in game-ID order as columns appear)
        // playerName → list of (gameId, score) pairs
        Map<String, List<long[]>> rawByPlayer = new LinkedHashMap<>();

        for (int row = 1; row < rows.size(); row++) {
            List<Object> cells = rows.get(row);
            if (cells.size() < 2) continue;
            String playerName = cells.get(1) == null ? "" : cells.get(1).toString().trim();
            if (playerName.isEmpty() || playerName.equals("-")) continue;

            List<long[]> scores = new ArrayList<>();
            for (Map.Entry<Integer, Long> e : colToGameId.entrySet()) {
                int col = e.getKey();
                long gameId = e.getValue();
                if (col >= cells.size()) continue;
                String raw = cells.get(col) == null ? "" : cells.get(col).toString().trim();
                if (raw.isEmpty() || raw.equals("-")) continue;
                try {
                    double score = Double.parseDouble(raw);
                    scores.add(new long[]{gameId, Double.doubleToLongBits(score)});
                } catch (NumberFormatException ex) { /* skip non-numeric */ }
            }
            if (!scores.isEmpty()) rawByPlayer.put(playerName, scores);
        }

        // Filter: for each player, discard leading entries where score == 50.0
        Map<Long, Map<String, Double>> result = new LinkedHashMap<>();
        int skipped = 0, kept = 0;
        for (Map.Entry<String, List<long[]>> e : rawByPlayer.entrySet()) {
            String player = e.getKey();
            boolean started = false;
            for (long[] pair : e.getValue()) {
                long gameId = pair[0];
                double score = Double.longBitsToDouble(pair[1]);
                if (!started && score == 50.0) { skipped++; continue; }
                started = true;
                result.computeIfAbsent(gameId, k -> new HashMap<>()).put(player, score);
                kept++;
            }
        }
        log.info("Sheet8 migration: kept {} entries, discarded {} leading-50 entries", kept, skipped);
        return result;
    }

    @Override
    public void save(Map<Long, Map<String, Double>> scores) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.asList("Game ID", "Player", "Score"));

        scores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(gameEntry -> {
                    long gameId = gameEntry.getKey();
                    gameEntry.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(playerEntry ->
                                    rows.add(Arrays.asList(gameId, playerEntry.getKey(),
                                            Math.round(playerEntry.getValue() * 100.0) / 100.0)));
                });

        connector.writeRange(RANGE, rows);
        log.info("Saved score cache: {} games, {} total entries",
                scores.size(), scores.values().stream().mapToInt(Map::size).sum());
    }
}
