package dao.daoInterface;

public interface GameInputDao {
    /**
     * Reads all game columns from the "Game Input" staging sheet
     * (format: Player Name | Finish on G{id} | Finish on G{id} | ...),
     * appends one correctly-ordered row per game to "Game Results", then clears the staging sheet.
     */
    void processInput();
}
