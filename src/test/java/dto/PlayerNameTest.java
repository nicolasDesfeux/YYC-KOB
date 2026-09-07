package dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Player name normalisation")
class PlayerNameTest {

    @Test
    @DisplayName("case differences resolve to the same player")
    void caseInsensitive() {
        assertEquals(Player.normaliseName("Chris Mitchell"),
                     Player.normaliseName("chris mitchell"));
        assertEquals(Player.normaliseName("CHRIS MITCHELL"),
                     Player.normaliseName("Chris Mitchell"));
    }

    @Test
    @DisplayName("surrounding whitespace is ignored — the bug that dropped results")
    void trimsSurroundingWhitespace() {
        assertEquals(Player.normaliseName("Chris Mitchell"),
                     Player.normaliseName("  Chris Mitchell "));
    }

    @Test
    @DisplayName("a doubled internal space still matches")
    void collapsesInternalWhitespace() {
        assertEquals(Player.normaliseName("Chris Mitchell"),
                     Player.normaliseName("Chris  Mitchell"));
    }

    @Test
    @DisplayName("a tab pasted from a spreadsheet still matches")
    void handlesTabs() {
        assertEquals(Player.normaliseName("Chris Mitchell"),
                     Player.normaliseName("Chris\tMitchell"));
    }

    @Test
    @DisplayName("different people stay distinct")
    void distinctNamesDoNotCollide() {
        assertNotEquals(Player.normaliseName("Chris Mitchell"),
                        Player.normaliseName("Chris Michell"));
    }

    @Test
    @DisplayName("null and blank collapse to empty rather than throwing")
    void nullSafe() {
        assertEquals("", Player.normaliseName(null));
        assertEquals("", Player.normaliseName("   "));
    }

    @Test
    @DisplayName("the display name keeps its original spelling")
    void displayNameUnchanged() {
        Player p = new Player("  Chris  Mitchell ");
        assertEquals("  Chris  Mitchell ", p.getName(), "stored name is untouched");
        assertEquals("chris mitchell", p.getNormalisedName(), "only the lookup key is canonical");
    }
}
