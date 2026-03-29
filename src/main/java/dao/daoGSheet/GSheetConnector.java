package dao.daoGSheet;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddBandingRequest;
import com.google.api.services.sheets.v4.model.BandedRange;
import com.google.api.services.sheets.v4.model.BandingProperties;
import com.google.api.services.sheets.v4.model.BasicFilter;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.ClearBasicFilterRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.DeleteBandingRequest;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SetBasicFilterRequest;
import com.google.api.services.sheets.v4.model.SortSpec;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.ValueRange;

import dto.GlobalStats;
import dto.Player;
import dto.PlayerStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GSheetConnector {

    private static final Logger log = LogManager.getLogger(GSheetConnector.class);

    private static final String APPLICATION_NAME = "kob-2023";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    // Twemoji CDN — medal emoji PNGs (stable, versioned URL)
    private static final String IMG_GOLD   = "=IMAGE(\"https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f947.png\")";
    private static final String IMG_SILVER = "=IMAGE(\"https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f948.png\")";
    private static final String IMG_BRONZE = "=IMAGE(\"https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/1f949.png\")";

    private final String spreadsheetId;
    private List<List<Object>> data;
    private Sheets sheetsService;

    public GSheetConnector(String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
    }

    private static HttpRequestInitializer getCredentials() throws IOException {
        InputStream in = GSheetConnector.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null)
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);

        return new HttpCredentialsAdapter(
                ServiceAccountCredentials
                        .fromStream(in)
                        .createScoped(SCOPES));
    }

    private Sheets getSheetsService() {
        if (sheetsService == null) {
            try {
                HttpRequestInitializer credentials = getCredentials();
                log.debug("Credentials retrieved successfully");
                sheetsService = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        credentials)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            } catch (IOException | GeneralSecurityException e) {
                log.error("ERROR ACCESSING THE SHEET", e);
                throw new RuntimeException("ERROR ACCESSING THE SHEET", e);
            }
        }
        return sheetsService;
    }

    /** Generic read — returns null on error or empty sheet. */
    public List<List<Object>> readRange(String range) {
        try {
            ValueRange response = getSheetsService().spreadsheets().values()
                    .get(spreadsheetId, range).execute();
            return response.getValues();
        } catch (IOException e) {
            log.error("Error reading range {}", range, e);
            return null;
        }
    }

    /** Generic write — clears the range then writes all rows. */
    public void writeRange(String range, List<List<Object>> rows) {
        try {
            getSheetsService().spreadsheets().values()
                    .clear(spreadsheetId, range, new ClearValuesRequest()).execute();
            getSheetsService().spreadsheets().values()
                    .update(spreadsheetId, range, new ValueRange().setValues(rows))
                    .setValueInputOption("RAW").execute();
        } catch (IOException e) {
            log.error("Error writing range {}", range, e);
            throw new RuntimeException(e);
        }
    }

    public List<List<Object>> getResults() {
        if (data == null) {
            try {
                Sheets service = getSheetsService();
                final String range = "Game Results!A1:ZZ1000";
                ValueRange response = service.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .execute();
                List<List<Object>> values = response.getValues();
                if (values == null || values.isEmpty()) {
                    log.error("No data found.");
                }
                data = values;
            } catch (IOException e) {
                log.error(e);
            }
        }
        return data;
    }

    public void writeRanking(List<Player> values) {
        Sheets service = getSheetsService();

        List<List<Object>> lines = new ArrayList<>();

        // Podium: classic 2nd | 1st | 3rd left-to-right layout (columns B, C, D)
        // Row 0: medal images, Row 1: names, Row 2: scores
        List<Object> podiumImages = new ArrayList<>(Arrays.asList("", IMG_SILVER, IMG_GOLD, IMG_BRONZE));
        List<Object> podiumNames  = new ArrayList<>(Arrays.asList("", "", "", ""));
        List<Object> podiumScores = new ArrayList<>(Arrays.asList("", "", "", ""));
        int[] podiumOrder = {1, 0, 2}; // 2nd, 1st, 3rd
        int[] podiumCols  = {1, 2, 3}; // columns B, C, D
        for (int p = 0; p < podiumOrder.length && podiumOrder[p] < values.size(); p++) {
            Player podiumPlayer = values.get(podiumOrder[p]);
            podiumNames.set(podiumCols[p], podiumPlayer.getName());
            podiumScores.set(podiumCols[p], podiumPlayer.getMasterScore().setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        lines.add(podiumImages);
        lines.add(podiumNames);
        lines.add(podiumScores);
        lines.add(new ArrayList<>()); // blank separator row

        // Ranking list
        LocalDateTime localDateTime = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of("Canada/Mountain");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String formattedDateTime = localDateTime.atZone(zoneId).format(formatter);

        List<Object> header = new ArrayList<>();
        header.add("Ranking as of: ");
        header.add(formattedDateTime);
        lines.add(header);

        for (int i = 0; i < values.size(); i++) {
            Player value = values.get(i);
            List<Object> detail = new ArrayList<>();
            detail.add(i + 1);
            detail.add(value.getName());
            detail.add(value.getMasterScore().setScale(2, RoundingMode.HALF_UP).doubleValue());
            lines.add(detail);
        }

        final String range = "Ranking!A1:EI1000";
        ValueRange body = new ValueRange().setValues(lines);
        try {
            service.spreadsheets().values()
                    .clear(spreadsheetId, range, new ClearValuesRequest()).execute();
            service.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED") // allows IMAGE() formulas to evaluate
                    .execute();
            applyPodiumFormatting(service);
            log.debug("Data written to the cell successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Applies gold/silver/bronze background colors and bold centered text
     * to the three podium columns (B=2nd, C=1st, D=3rd) in rows 1-3.
     */
    private void applyPodiumFormatting(Sheets service) throws IOException {
        Integer sheetId = service.spreadsheets().get(spreadsheetId).execute()
                .getSheets().stream()
                .filter(s -> "Ranking".equals(s.getProperties().getTitle()))
                .findFirst()
                .map(s -> s.getProperties().getSheetId())
                .orElseThrow(() -> new IllegalStateException("Ranking sheet not found"));

        Color gold   = new Color().setRed(1.0f).setGreen(0.843f).setBlue(0.0f);
        Color silver = new Color().setRed(0.753f).setGreen(0.753f).setBlue(0.753f);
        Color bronze = new Color().setRed(0.804f).setGreen(0.498f).setBlue(0.196f);

        int[]   cols   = {1,      2,    3};      // B=2nd, C=1st, D=3rd
        Color[] colors = {silver, gold, bronze};

        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < cols.length; i++) {
            requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(0).setEndRowIndex(3)
                            .setStartColumnIndex(cols[i]).setEndColumnIndex(cols[i] + 1))
                    .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                            .setBackgroundColor(colors[i])
                            .setTextFormat(new TextFormat().setBold(true))
                            .setHorizontalAlignment("CENTER")))
                    .setFields("userEnteredFormat(backgroundColor,textFormat,horizontalAlignment)")));
        }

        service.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }

    public void writeStatistics(GlobalStats globalStats, List<PlayerStats> playerStats) {
        Sheets service = getSheetsService();
        List<List<Object>> lines = new ArrayList<>();
        int row = 0;

        // --- Global stats section ---
        int globalTitleRow = row++;
        lines.add(Arrays.asList("Global Statistics"));
        int globalHeaderRow = row++;
        lines.add(Arrays.asList("Category", "Player", "Count", "Rate"));
        int globalDataStart = row;
        lines.add(statRow("Most games played",                globalStats.mostGamesPlayed,    globalStats.mostGamesPlayedCount,  null)); row++;
        for (int t = 1; t <= globalStats.maxTier; t++) {
            lines.add(statRow("Most games in Tier " + t,     globalStats.mostGamesPerTier.get(t),  globalStats.mostGamesPerTierCount.get(t), null)); row++;
        }
        lines.add(statRow("Most wins",                        globalStats.mostWins,           globalStats.mostWinsCount,         globalStats.mostWinsRate)); row++;
        for (int t = 1; t <= globalStats.maxTier; t++) {
            lines.add(statRow("Most wins in Tier " + t,      globalStats.mostWinsPerTier.get(t),   globalStats.mostWinsPerTierCount.get(t),  globalStats.mostWinsPerTierRate.get(t))); row++;
        }
        lines.add(statRow("Most moved up",                    globalStats.mostMovedUp,        globalStats.mostMovedUpCount,      globalStats.mostMovedUpRate)); row++;
        lines.add(statRow("Most moved down",                  globalStats.mostMovedDown,      globalStats.mostMovedDownCount,    globalStats.mostMovedDownRate)); row++;
        lines.add(statRow("Most consistent (stayed in tier)", globalStats.mostConsistent,     globalStats.mostConsistentCount,   globalStats.mostConsistentRate)); row++;
        int globalDataEnd = row;
        lines.add(Collections.emptyList()); row++;
        lines.add(Collections.emptyList()); row++;

        // --- Individual stats section ---
        int individualTitleRow = row++;
        lines.add(Arrays.asList("Individual Statistics"));
        int individualHeaderRow = row++;
        List<Object> header = new ArrayList<>(Arrays.asList("Player", "Games Played", "Participation", "Wins", "Win%"));
        for (int t = 1; t <= globalStats.maxTier; t++) {
            header.add("T" + t + " Games"); header.add("T" + t + " Games%");
            header.add("T" + t + " Wins");  header.add("T" + t + " Win%");
        }
        header.addAll(Arrays.asList("Moved Up", "Move Up%", "Moved Down", "Move Down%", "Top Tier", "Best Teammate", "Worst Teammate"));
        int headerColCount = header.size();
        lines.add(header);
        int individualDataStart = row;
        for (PlayerStats ps : playerStats) {
            List<Object> dataRow = new ArrayList<>();
            dataRow.add(ps.player.getName());
            dataRow.add(ps.gamesPlayed);
            dataRow.add(pct(ps.participationRate));
            dataRow.add(ps.totalWins);
            dataRow.add(pct(ps.gamesPlayed > 0 ? (double) ps.totalWins / ps.gamesPlayed : 0));
            for (int t = 1; t <= globalStats.maxTier; t++) {
                int gamesInTier = ps.gamesByTier.getOrDefault(t, 0);
                int winsInTier  = ps.winsByTier.getOrDefault(t, 0);
                dataRow.add(gamesInTier);
                dataRow.add(pct(ps.gamesPlayed > 0 ? (double) gamesInTier / ps.gamesPlayed : 0));
                dataRow.add(winsInTier);
                dataRow.add(pct(gamesInTier > 0 ? (double) winsInTier / gamesInTier : 0));
            }
            dataRow.add(ps.movesUp);
            dataRow.add(pct(ps.gamesPlayed > 0 ? (double) ps.movesUp   / ps.gamesPlayed : 0));
            dataRow.add(ps.movesDown);
            dataRow.add(pct(ps.gamesPlayed > 0 ? (double) ps.movesDown / ps.gamesPlayed : 0));
            dataRow.add("Tier " + ps.mostPlayedTier);
            dataRow.add(ps.bestTeammate  != null ? ps.bestTeammate.getName()  : "-");
            dataRow.add(ps.worstTeammate != null ? ps.worstTeammate.getName() : "-");
            lines.add(dataRow); row++;
        }
        int individualDataEnd = row;

        final String range = "Statistics!A1:ZZ1000";
        try {
            service.spreadsheets().values().clear(spreadsheetId, range, new ClearValuesRequest()).execute();
            service.spreadsheets().values()
                    .update(spreadsheetId, range, new ValueRange().setValues(lines))
                    .setValueInputOption("RAW")
                    .execute();
            applyStatisticsFormatting(service,
                    globalTitleRow, globalHeaderRow, globalDataStart, globalDataEnd,
                    individualTitleRow, individualHeaderRow, individualDataStart, individualDataEnd,
                    headerColCount);
            log.debug("Statistics written successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyStatisticsFormatting(Sheets service,
            int globalTitleRow, int globalHeaderRow, int globalDataStart, int globalDataEnd,
            int individualTitleRow, int individualHeaderRow, int individualDataStart, int individualDataEnd,
            int headerColCount) throws IOException {

        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId)
                .setFields("sheets(properties,bandedRanges)").execute();
        Sheet statsSheet = spreadsheet.getSheets().stream()
                .filter(s -> "Statistics".equals(s.getProperties().getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Statistics sheet not found"));
        int sheetId = statsSheet.getProperties().getSheetId();

        Color darkBlue  = new Color().setRed(0.118f).setGreen(0.267f).setBlue(0.439f); // #1E4470
        Color medBlue   = new Color().setRed(0.267f).setGreen(0.447f).setBlue(0.769f); // #4472C4
        Color white     = new Color().setRed(1.0f).setGreen(1.0f).setBlue(1.0f);
        Color lightBlue = new Color().setRed(0.878f).setGreen(0.914f).setBlue(0.980f); // #E0EBFA
        Color lightGray = new Color().setRed(0.949f).setGreen(0.949f).setBlue(0.949f); // #F2F2F2

        List<Request> requests = new ArrayList<>();

        // Remove stale banded ranges and filters before re-applying
        if (statsSheet.getBandedRanges() != null)
            for (BandedRange br : statsSheet.getBandedRanges())
                requests.add(new Request().setDeleteBanding(new DeleteBandingRequest().setBandedRangeId(br.getBandedRangeId())));
        requests.add(new Request().setClearBasicFilter(new ClearBasicFilterRequest().setSheetId(sheetId)));

        // Section title rows
        requests.add(headerRow(sheetId, globalTitleRow,     0, 4,            darkBlue, white, 13));
        requests.add(headerRow(sheetId, individualTitleRow, 0, headerColCount, darkBlue, white, 13));

        // Column header rows
        requests.add(headerRow(sheetId, globalHeaderRow,     0, 4,            medBlue, white, 11));
        requests.add(headerRow(sheetId, individualHeaderRow, 0, headerColCount, medBlue, white, 11));

        // Alternating row colors for data sections
        requests.add(new Request().setAddBanding(new AddBandingRequest().setBandedRange(new BandedRange()
                .setRange(gridRange(sheetId, globalDataStart, globalDataEnd, 0, 4))
                .setRowProperties(new BandingProperties().setFirstBandColor(white).setSecondBandColor(lightGray)))));
        requests.add(new Request().setAddBanding(new AddBandingRequest().setBandedRange(new BandedRange()
                .setRange(gridRange(sheetId, individualDataStart, individualDataEnd, 0, headerColCount))
                .setRowProperties(new BandingProperties().setFirstBandColor(white).setSecondBandColor(lightBlue)))));

        // AutoFilter on individual stats header
        requests.add(new Request().setSetBasicFilter(new SetBasicFilterRequest()
                .setFilter(new BasicFilter()
                        .setRange(gridRange(sheetId, individualHeaderRow, individualDataEnd, 0, headerColCount))
                        .setSortSpecs(Collections.singletonList(
                                new SortSpec().setDimensionIndex(0).setSortOrder("ASCENDING"))))));

        service.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }

    private static Request headerRow(int sheetId, int row, int colStart, int colEnd,
            Color bg, Color fg, int fontSize) {
        return new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(gridRange(sheetId, row, row + 1, colStart, colEnd))
                .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                        .setBackgroundColor(bg)
                        .setTextFormat(new TextFormat().setForegroundColor(fg).setBold(true).setFontSize(fontSize))))
                .setFields("userEnteredFormat(backgroundColor,textFormat)"));
    }

    private static GridRange gridRange(int sheetId, int startRow, int endRow, int startCol, int endCol) {
        return new GridRange().setSheetId(sheetId)
                .setStartRowIndex(startRow).setEndRowIndex(endRow)
                .setStartColumnIndex(startCol).setEndColumnIndex(endCol);
    }

    private static List<Object> statRow(String label, Player player, int count, Double rate) {
        String name = player != null ? player.getName() : "-";
        String rateStr = rate != null ? pct(rate) : "";
        return Arrays.asList(label, name, count, rateStr);
    }

    private static String pct(double rate) {
        return String.format("%.1f%%", rate * 100);
    }

    public void writeMasterScores(Map<Player, List<String>> masterScoresEvolution) {
        Sheets service = getSheetsService();

        List<List<Object>> lines = new ArrayList<>();
        for (Player player : masterScoresEvolution.keySet().stream()
                .sorted(Comparator.comparing(Player::getMasterScore)).collect(Collectors.toList())) {
            List<Object> detail = new ArrayList<>();
            detail.add(player.getName());
            detail.addAll(masterScoresEvolution.get(player));
            lines.add(detail);
        }

        final String range = "MasterScores!A1:ZI1000";
        ValueRange body = new ValueRange().setValues(lines);
        try {
            service.spreadsheets().values().clear(spreadsheetId, range, new ClearValuesRequest()).execute();
            service.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute();
            log.debug("Data written to the cell successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
