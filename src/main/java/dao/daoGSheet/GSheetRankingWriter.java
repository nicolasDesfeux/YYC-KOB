package dao.daoGSheet;

import dao.daoInterface.RankingWriter;
import dto.GlobalStats;
import dto.Player;
import dto.PlayerStats;

import java.util.List;
import java.util.Map;

public class GSheetRankingWriter implements RankingWriter {

    private final GSheetConnector connector;

    public GSheetRankingWriter(GSheetConnector connector) {
        this.connector = connector;
    }

    @Override
    public void writeRanking(List<Player> players, Map<Player, List<String>> masterScoresEvolution) {
        connector.writeRanking(players);
        connector.writeMasterScores(masterScoresEvolution);
    }

    @Override
    public void writeStatistics(GlobalStats globalStats, List<PlayerStats> playerStats) {
        connector.writeStatistics(globalStats, playerStats);
    }
}
