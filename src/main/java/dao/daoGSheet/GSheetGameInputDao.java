package dao.daoGSheet;

import dao.daoInterface.GameInputDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * Expected "Game Input" sheet layout:
 *   Row 1: A = "Players", B+ = one column per game, headed with its ID ("G451")
 *   Row 2: A = "Date",    B+ = that game's date, ISO format (2026-09-02)
 *   Row 3+: A = player name, B+ = that player's finish position
 *
 * The date row is required and doubles as a ready marker: a game column with no
 * valid date is left in place rather than imported, so a game can be staged
 * while it is still being entered without a scheduled run picking it up early.
 *
 * New players are automatically added as new columns to the "Game Results" header.
 */
public class GSheetGameInputDao implements GameInputDao {

    private static final Logger log = LogManager.getLogger(GSheetGameInputDao.class);
    private static final String INPUT_SHEET  = "Game Input";
    private static final String RESULT_SHEET = "Game Results";
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("G(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Row 1 is the header, row 2 the dates, so player rows start here. */
    private static final int FIRST_PLAYER_ROW = 2;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Where a game's finishes live in the staging sheet, and when it was played. */
    private record GameColumn(int column, LocalDate date) {}

    private final GSheetConnector connector;

    public GSheetGameInputDao(GSheetConnector connector) {
        this.connector = connector;
    }

    /** True when row 2 looks like the date row (column A labelled "date"). */
    static boolean isDateRow(List<Object> row) {
        if (row == null || row.isEmpty() || row.get(0) == null) return false;
        String label = row.get(0).toString().trim().toLowerCase(java.util.Locale.ROOT)
                .replace("(", "").replace(")", "").replace(":", "").trim();
        return label.equals("date") || label.equals("dates");
    }

    /** Parses a cell as an ISO date, returning null for anything unusable. */
    static LocalDate parseDate(Object cell) {
        if (cell == null) return null;
        String raw = cell.toString().trim();
        if (raw.isEmpty()) return null;
        try {
            return LocalDate.parse(raw, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
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

        // ── 3. Parse header and the date row ─────────────────────────────────
        List<Object> inputHeader = input.get(0);
        List<Object> dateRow = input.get(1);
        if (!isDateRow(dateRow)) {
            log.error("'{}' row 2 must be the date row: column A labelled 'Date', "
                    + "each game column holding that game's date as yyyy-MM-dd. "
                    + "Nothing imported.", INPUT_SHEET);
            return;
        }
        if (input.size() < 3) {
            log.error("'{}' has a header and date row but no player rows", INPUT_SHEET);
            return;
        }

        // gameId → where to read it and when it was played
        Map<Long, GameColumn> gameColumns = new LinkedHashMap<>();
        int undated = 0;
        for (int j = 1; j < inputHeader.size(); j++) {
            if (inputHeader.get(j) == null) continue;
            Matcher m = GAME_ID_PATTERN.matcher(inputHeader.get(j).toString());
            if (!m.find()) continue;
            long gameId = Long.parseLong(m.group(1));

            LocalDate date = parseDate(j < dateRow.size() ? dateRow.get(j) : null);
            if (date == null) {
                log.warn("Game {} has no valid date in '{}' — leaving it staged", gameId, INPUT_SHEET);
                undated++;
                continue;
            }
            gameColumns.put(gameId, new GameColumn(j, date));
            log.info("Staged game {} played {}", gameId, date);
        }
        if (gameColumns.isEmpty()) {
            log.info("No dated game columns in '{}' — nothing to import", INPUT_SHEET);
            return;
        }

        // ── 4. Register new players — extend Game Results header if needed ────
        List<String> newPlayers = new ArrayList<>();
        for (int i = FIRST_PLAYER_ROW; i < input.size(); i++) {
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
        int imported = 0, skipped = 0;
        for (Map.Entry<Long, GameColumn> entry : gameColumns.entrySet()) {
            long gameId   = entry.getKey();
            int  inputCol = entry.getValue().column();

            if (existingGameIds.contains(gameId)) {
                log.warn("Game {} already exists in '{}' — skipping", gameId, RESULT_SHEET);
                skipped++;
                continue;
            }

            List<Object> newRow = new ArrayList<>(Collections.nCopies(resultHeader.size(), ""));
            newRow.set(0, gameId);
            newRow.set(1, entry.getValue().date().format(DATE_FORMAT));

            int matched = 0, unmatched = 0;
            for (int i = FIRST_PLAYER_ROW; i < input.size(); i++) {
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
            imported++;
            log.info("Appended game {} ({}) to '{}'", gameId, entry.getValue().date(), RESULT_SHEET);
        }

        // ── 6. Clear staging only when nothing was left behind ───────────────
        // Undated or duplicate games are still sitting in the sheet; wiping it
        // would destroy input that was never imported.
        if (imported > 0 && skipped == 0 && undated == 0) {
            connector.clearSheetData(INPUT_SHEET);
            log.info("Imported {} game(s); '{}' staging sheet cleared", imported, INPUT_SHEET);
        } else {
            log.info("Imported {} game(s); leaving '{}' in place ({} undated, {} duplicate)",
                    imported, INPUT_SHEET, undated, skipped);
        }
    }
}
