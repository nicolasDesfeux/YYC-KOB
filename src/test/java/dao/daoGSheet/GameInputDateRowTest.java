package dao.daoGSheet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The date row is what keeps an unattended run safe: it supplies the date that
 * would otherwise be filled in by hand, and a game without one is left staged
 * rather than imported half-entered.
 */
@DisplayName("Game Input date row")
class GameInputDateRowTest {

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    @Test
    @DisplayName("row 2 is recognised however the label is written")
    void recognisesTheDateRow() {
        assertTrue(GSheetGameInputDao.isDateRow(row("Date", "2026-09-02")));
        assertTrue(GSheetGameInputDao.isDateRow(row("date", "2026-09-02")));
        assertTrue(GSheetGameInputDao.isDateRow(row("(date)", "2026-09-02")));
        assertTrue(GSheetGameInputDao.isDateRow(row("  Date:  ", "2026-09-02")));
        assertTrue(GSheetGameInputDao.isDateRow(row("Dates", "2026-09-02")));
    }

    @Test
    @DisplayName("a player row is not mistaken for the date row")
    void rejectsAPlayerRow() {
        assertFalse(GSheetGameInputDao.isDateRow(row("Chris Mitchell", "1.5")));
        assertFalse(GSheetGameInputDao.isDateRow(row("", "2026-09-02")));
        assertFalse(GSheetGameInputDao.isDateRow(row()));
        assertFalse(GSheetGameInputDao.isDateRow(null));
    }

    @Test
    @DisplayName("ISO dates parse")
    void parsesIsoDates() {
        assertEquals(LocalDate.of(2026, 9, 2), GSheetGameInputDao.parseDate("2026-09-02"));
        assertEquals(LocalDate.of(2026, 9, 2), GSheetGameInputDao.parseDate("  2026-09-02  "));
    }

    @Test
    @DisplayName("an unusable date yields null so the game stays staged")
    void rejectsUnusableDates() {
        assertNull(GSheetGameInputDao.parseDate(null),          "blank cell");
        assertNull(GSheetGameInputDao.parseDate(""),            "empty string");
        assertNull(GSheetGameInputDao.parseDate("   "),         "whitespace only");
        assertNull(GSheetGameInputDao.parseDate("Sept 2 2026"), "wrong format");
        assertNull(GSheetGameInputDao.parseDate("02/09/2026"),  "wrong format");
        assertNull(GSheetGameInputDao.parseDate("2026-13-45"),  "not a real date");
    }
}
