package dao.daoGSheet;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import dto.Player;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

public class GSheetConnector {
    private static final String APPLICATION_NAME = "kob-2023";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

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
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        InputStream in = GSheetConnector.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private static Sheets getSheetsService() {
        // Build a new authorized API client service.
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("ERROR ACCESSING THE SHEET", e);
        }


    }

    public static List<List<Object>> getResults() throws IOException, GeneralSecurityException {
        if (data == null) {
            Sheets service = getSheetsService();

            final String range = "Game Results!A1:EI1000";

            ValueRange response = service.spreadsheets().values()
                    .get(SPREADSHEET_ID, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                System.out.println("No data found.");
            }
            data = values;
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
