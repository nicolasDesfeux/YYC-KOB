package dao.daoJdbc;

import dao.daoInterface.RankingWriter;
import dto.Player;
import kob.KOB;

import java.util.List;
import java.util.Map;

public class ConsoleRankingWriter implements RankingWriter {

    @Override
    public void writeRanking(List<Player> players, Map<Player, List<String>> masterScoresEvolution) {
        StringBuilder rankingString = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            rankingString.append(i + 1).append(". ").append(players.get(i).getName()).append(" (")
                    .append(KOB.DF.format(players.get(i).getMasterScore())).append(")\n");
        }
        System.out.println(rankingString.toString());
    }
}
