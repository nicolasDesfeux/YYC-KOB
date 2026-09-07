package dao.daoGSheet;

import dao.daoInterface.GameInputDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads player finish positions from the "Game Input" staging sheet and appends
 * one correctly-ordered row per game to the "Game Results" sheet.
 *
 * Expected "Game Input" sheet layout (row 1 = header):
 *   A: Players
 *   B+: One column per game, header is the game ID (e.g. "G451", "G452")
 *
 * Dates are left blank — fill them manually in "Game Results".
 * New players are automatically added as new columns to the "Game Results" header.
 */
public class GSheetGameInputDao implements GameInputDao {

    private static final Logger log = LogManager.getLogger(GSheetGameInputDao.class);
    private static final String INPUT_SHEET  = "Game Input";
    private static final String RESULT_SHEET = "Game Results";
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("G(\\d+)", Pattern.CASE_INSENSITIVE);

    private final GSheetConnector connector;

    public GSheetGameInputDao(GSheetConnector connector) {
        this.connector = connector;
    }

    @Override
    public void processInput() {
        // ── 1. Read results header to get player→column mapping ──────────────
        List<List<Object>> results = connector.getResults();
        if (results == null || results.isEmpty()) {
            log.error("Game Results sheet is empty — cannot add game");
            return;
        }
        // Mutable copy so we can extend it with new players
        List<Object> resultHeader = new ArrayList<>(results.get(0));
        // Keyed on the canonical name so the importer and the scorer agree on identity.
        Map<String, Integer> playerCol = new HashMap<>();
        for (int j = 2; j < resultHeader.size(); j++) {
            if (resultHeader.get(j) != null && !resultHeader.get(j).toString().isBlank())
                playerCol.put(dto.Player.normaliseName(resultHeader.get(j).toString()), j);
        }
        log.debug("Game Results header has {} player columns", playerCol.size());

        // Build set of existing game IDs to detect duplicates
        Set<Long> existingGameIds = new HashSet<>();
        for (int i = 1; i < results.size(); i++) {
            List<Object> row = results.get(i);
            if (!row.isEmpty() && row.get(0) != null && !row.get(0).toString().isBlank()) {
                try { existingGameIds.add(Long.parseLong(row.get(0).toString().trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        log.debug("Game Results has {} existing game rows", existingGameIds.size());

        // ── 2. Read Game Input staging sheet ─────────────────────────────────
        List<List<Object>> input = connector.readRange("'" + INPUT_SHEET + "'!A1:ZZ1000");
        if (input == null || input.size() < 2) {
            log.error("'{}' sheet is empty or has no data rows", INPUT_SHEET);
            return;
        }

        // ── 3. Parse header: find game columns (must contain G{id}) ───────────
        List<Object> inputHeader = input.get(0);
        // ordered map: gameId → input column index
        Map<Long, Integer> gameColumns = new LinkedHashMap<>();
        for (int j = 1; j < inputHeader.size(); j++) {
            if (inputHeader.get(j) == null) continue;
            String colHeader = inputHeader.get(j).toString();
            Matcher m = GAME_ID_PATTERN.matcher(colHeader);
            if (m.find()) {
                long gameId = Long.parseLong(m.group(1));
                gameColumns.put(gameId, j);
                log.debug("Found game column: '{}' → game ID {}", colHeader, gameId);
            }
        }
        if (gameColumns.isEmpty()) {
            log.error("No game columns found in '{}' header (expected 'G<id>' in column names)", INPUT_SHEET);
            return;
        }

        // ── 4. Register new players — extend Game Results header if needed ────
        List<String> newPlayers = new ArrayList<>();
        for (int i = 1; i < input.size(); i++) {
            List<Object> row = input.get(i);
            if (row.isEmpty() || row.get(0) == null) continue;
            String name = row.get(0).toString().trim();
            if (!name.isEmpty() && !playerCol.containsKey(dto.Player.normaliseName(name))
                    && newPlayers.stream().noneMatch(n -> dto.Player.normaliseName(n).equals(dto.Player.normaliseName(name)))) {
                newPlayers.add(name);
            }
        }
        if (!newPlayers.isEmpty()) {
            log.info("New player(s) detected — adding to '{}' header: {}", RESULT_SHEET, newPlayers);
            for (String name : newPlayers) {
                int newCol = resultHeader.size();
                resultHeader.add(name);
                playerCol.put(dto.Player.normaliseName(name), newCol);
                log.debug("  {} → column {}", name, newCol);
            }
            connector.writeHeader(RESULT_SHEET, resultHeader);
            log.info("'{}' header updated ({} columns total)", RESULT_SHEET, resultHeader.size());
        }

        // ── 5. For each game, build and append a row ──────────────────────────
        for (Map.Entry<Long, Integer> entry : gameColumns.entrySet()) {
            long gameId   = entry.getKey();
            int  inputCol = entry.getValue();

            if (existingGameIds.contains(gameId)) {
                log.warn("Game {} already exists in '{}' — skipping", gameId, RESULT_SHEET);
                continue;
            }

            List<Object> newRow = new ArrayList<>(Collections.nCopies(resultHeader.size(), ""));
            newRow.set(0, gameId);
            newRow.set(1, "");   // date left blank — fill manually

            int matched = 0, unmatched = 0;
            for (int i = 1; i < input.size(); i++) {
                List<Object> row = input.get(i);
                if (row.isEmpty()) continue;
                String playerName = row.get(0) == null ? "" : row.get(0).toString().trim();
                if (playerName.isEmpty()) continue;

                Object finishVal = inputCol < row.size() ? row.get(inputCol) : null;
                if (finishVal == null || finishVal.toString().isBlank()) {
                    log.debug("Row {}: no finish for '{}' in game {} — skipping", i + 1, playerName, gameId);
                    continue;
                }

                Integer col = playerCol.get(dto.Player.normaliseName(playerName));
                if (col == null) {
                    log.warn("Player '{}' not found in Game Results header — skipping", playerName);
                    unmatched++;
                    continue;
                }
                newRow.set(col, finishVal);
                matched++;
            }
            log.info("Game {}: matched {} players, {} unmatched", gameId, matched, unmatched);

            connector.appendRow(RESULT_SHEET, newRow);
            log.info("Appended game {} to '{}'", gameId, RESULT_SHEET);
        }

        // ── 6. Clear staging sheet (keep header row) ─────────────────────────
        connector.clearSheetData(INPUT_SHEET);
        log.info("'{}' staging sheet cleared", INPUT_SHEET);
    }
}
