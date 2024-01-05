package dao.daoGSheet;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import dto.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class GSheetConnector {

    private static final Logger log = LogManager.getLogger(GSheetConnector.class);

    private static final String APPLICATION_NAME = "kob-2023";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES =
            Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private static final String SPREADSHEET_ID = "1gwQcMQZ_Cibusv_6deeVgKBSgnhRuUy3mU8u9Y-bF_g";
    private static List<List<Object>> data;

    /**
     * Creates an authorized Credential object.
     *
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials()
            throws IOException {
        // Load client secrets.
        InputStream in = GSheetConnector.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        return GoogleCredential.fromStream(in)
                .createScoped(SCOPES);
    }

    private static Sheets getSheetsService() {
        // Build a new authorized API client service.
        try {
            Credential credentials = getCredentials();
            log.debug("Credentials retrieved succesfully");
            return new Sheets.Builder(credentials.getTransport(), credentials.getJsonFactory(), credentials)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (IOException e) {
            log.error("ERROR ACCESSING THE SHEET",e);
            throw new RuntimeException("ERROR ACCESSING THE SHEET", e);
        }


    }

    public static List<List<Object>> getResults() {
        if (data == null) {

            try {
                Sheets service = getSheetsService();

                final String range = "Game Results!A1:HI1000";

                ValueRange response = service.spreadsheets().values()
                        .get(SPREADSHEET_ID, range)
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

    public static void writeRanking(List<Player> values) {
        // Build a Sheets service
        Sheets sheetsService = getSheetsService();

        List<List<Object>> lines = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Player value = values.get(i);
            List<Object> detail = new ArrayList<>();
            detail.add(i+1);
            detail.add(value.getName());
            detail.add(value.getMasterScore().setScale(2, RoundingMode.HALF_UP).doubleValue());
            lines.add(detail);
        }
        List<Object> detail = new ArrayList<>();
        detail.add("Ranking as of: ");

        // Create a LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.now();

        // Define the time zone (replace "America/New_York" with your desired time zone)
        ZoneId zoneId = ZoneId.of("Canada/Mountain");

        // Create a DateTimeFormatter with the desired pattern
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Format the LocalDateTime in the specified time zone
        String formattedDateTime = localDateTime.atZone(zoneId).format(formatter);
        
        detail.add(formattedDateTime);
        lines.add(0,detail);
        // Create a ValueRange object
        ValueRange body = new ValueRange()
                .setValues(lines);

        final String range = "Ranking!A1:EI1000";
        // Write data to the specified cell
        try {
            sheetsService.spreadsheets().values()
                    .update(SPREADSHEET_ID, range, body)
                    .setValueInputOption("RAW")
                    .execute();
            System.out.println("Data written to the cell successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



    }

    public static void writeMasterScores(Map<Player, List<String>> masterScoresEvolution) {

        // Build a Sheets service
        Sheets sheetsService = getSheetsService();

        List<List<Object>> lines = new ArrayList<>();
        for (Player player : masterScoresEvolution.keySet().stream().sorted(Comparator.comparing(Player::getMasterScore)).collect(Collectors.toList())) {
            List<Object> detail = new ArrayList<>();
            detail.add(player.getName());
            detail.addAll(masterScoresEvolution.get(player));
            lines.add(detail);
        }
        // Create a ValueRange object
        ValueRange body = new ValueRange()
                .setValues(lines);

        final String range = "MasterScores!A1:ZI1000";
        // Write data to the specified cell
        try {
            sheetsService.spreadsheets().values().clear(SPREADSHEET_ID, range, new ClearValuesRequest()).execute();
            sheetsService.spreadsheets().values()
                    .update(SPREADSHEET_ID, range, body)
                    .setValueInputOption("RAW")
                    .execute();
            System.out.println("Data written to the cell successfully!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
