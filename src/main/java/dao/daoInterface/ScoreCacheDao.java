package dao.daoInterface;

import java.util.Map;

/**
 * Persists and retrieves the computed master score for every player after every game.
 *
 * The cache is the authoritative source for historical scores:
 * - Values already in the cache (including manual overrides) are used as-is.
 * - Scores for games not yet in the cache are calculated and then written back.
 */
public interface ScoreCacheDao {

    /**
     * Loads all stored scores.
     * @return Map of gameId → (playerName → masterScoreAfterGame)
     */
    Map<Long, Map<String, Double>> load();

    /**
     * Persists the full score history, overwriting whatever was in the store.
     * @param scores Map of gameId → (playerName → masterScoreAfterGame)
     */
    void save(Map<Long, Map<String, Double>> scores);
}
