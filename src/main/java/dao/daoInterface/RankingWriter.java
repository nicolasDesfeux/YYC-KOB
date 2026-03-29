package dao.daoInterface;

import dto.GlobalStats;
import dto.Player;
import dto.PlayerStats;

import java.util.List;
import java.util.Map;

public interface RankingWriter {
    void writeRanking(List<Player> players, Map<Player, List<String>> masterScoresEvolution);

    default void writeStatistics(GlobalStats globalStats, List<PlayerStats> playerStats) {
        // default: no-op (e.g. console writer)
    }
}
