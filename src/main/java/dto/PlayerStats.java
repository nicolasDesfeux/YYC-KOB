package dto;

import java.util.Map;

public class PlayerStats {

    public final Player player;
    public final int gamesPlayed;
    public final double participationRate;
    public final int totalWins;
    public final Map<Integer, Integer> winsByTier;    // finishing tier -> win count
    public final Map<Integer, Integer> gamesByTier;   // finishing tier -> games played in that tier
    public final int movesUp;
    public final int movesDown;
    public final int mostPlayedTier;
    public final Player bestTeammate;
    public final Player worstTeammate;

    public PlayerStats(Player player, int gamesPlayed, double participationRate,
                       int totalWins, Map<Integer, Integer> winsByTier, Map<Integer, Integer> gamesByTier,
                       int movesUp, int movesDown, int mostPlayedTier,
                       Player bestTeammate, Player worstTeammate) {
        this.player = player;
        this.gamesPlayed = gamesPlayed;
        this.participationRate = participationRate;
        this.totalWins = totalWins;
        this.winsByTier = winsByTier;
        this.gamesByTier = gamesByTier;
        this.movesUp = movesUp;
        this.movesDown = movesDown;
        this.mostPlayedTier = mostPlayedTier;
        this.bestTeammate = bestTeammate;
        this.worstTeammate = worstTeammate;
    }
}
